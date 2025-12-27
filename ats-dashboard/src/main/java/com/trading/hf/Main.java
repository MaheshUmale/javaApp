package com.trading.hf;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.lmax.disruptor.EventHandler;
import com.trading.hf.dashboard.DashboardBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.trading.hf.alphapulse.AlphaPulseEngine;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String runMode = ConfigLoader.getProperty("run.mode", "live");
        String indexName = ConfigLoader.getProperty("index.name", "NIFTY");
        String indexSuffix = ConfigLoader.getProperty("index.suffix", "50");
        String indexHeavyweightsFile = ConfigLoader.getProperty("index.heavyweights.file", "IndexWeights.json");
        String indexInstrumentKey = ConfigLoader.getProperty("index.instrument.key", "NSE_INDEX|Nifty 50");
        String indexSpotSymbol = ConfigLoader.getProperty("index.spot.symbol", "Nifty 50");

        if (args.length > 0) {
            runMode = args[0];
        }

        boolean questDbEnabled = ConfigLoader.getBooleanProperty("questdb.enabled", false);
        String dataDirectory = "data";

        boolean dashboardEnabled = ConfigLoader.getBooleanProperty("dashboard.enabled", true);
        QuestDBWriter questDBWriter = questDbEnabled ? new QuestDBWriter() : null;
        RawFeedWriter rawFeedWriter = new RawFeedWriter();

        InstrumentMaster instrumentMaster = new InstrumentMaster("instrument-master.json");
        IndexWeightCalculator indexWeightCalculator = new IndexWeightCalculator(indexHeavyweightsFile, indexName + indexSuffix, instrumentMaster);
        OptionChainProvider optionChainProvider = new OptionChainProvider(instrumentMaster, indexInstrumentKey, indexSpotSymbol);
        PositionManager positionManager = new PositionManager();
        UpstoxOrderManager orderManager = new UpstoxOrderManager(null, positionManager);
        ThetaExitGuard thetaExitGuard = new ThetaExitGuard(positionManager, orderManager);

        SignalPersistenceWriter signalPersistenceWriter = questDbEnabled ? new SignalPersistenceWriter() : null;
        OrderPersistenceWriter orderPersistenceWriter = questDbEnabled ? new OrderPersistenceWriter() : null;
        TelemetryWriter telemetryWriter = questDbEnabled ? new TelemetryWriter() : null;
        HeavyweightWriter heavyweightWriter = questDbEnabled ? new HeavyweightWriter() : null;

        VirtualPositionManager virtualPositionManager = new VirtualPositionManager();
        PaperTradingEngine paperTradingEngine = new PaperTradingEngine(virtualPositionManager);

        List<EventHandler<MarketEvent>> marketEventHandlers = new ArrayList<>();

        DisruptorManager disruptorManager = new DisruptorManager(
                questDBWriter,
                rawFeedWriter,
                marketEventHandlers,
                indexWeightCalculator,
                optionChainProvider,
                thetaExitGuard,
                signalPersistenceWriter,
                orderPersistenceWriter,
                telemetryWriter,
                heavyweightWriter,
                List.of((event, seq, end) -> DashboardBridge.onMarketEvent(event)),
                paperTradingEngine);

        AlphaPulseEngine alphaPulseEngine = new AlphaPulseEngine(disruptorManager.getSignalRingBuffer(), indexInstrumentKey, instrumentMaster);

        marketEventHandlers.add((event, sequence, endOfBatch) -> alphaPulseEngine.onMarketEvent(event));

        disruptorManager.start();

        InstrumentLoader loader = new InstrumentLoader("instruments.db", "NSE.JSON.gz", "NSE.json");
        AutoInstrumentManager autoInstrumentManager = new AutoInstrumentManager(loader, "mapped_instruments.json");
        autoInstrumentManager.initialize();
        DashboardBridge.setNiftyFutureKey(autoInstrumentManager.getNiftyFutureKey());

        orderManager.setOrderRingBuffer(disruptorManager.getOrderRingBuffer());
        indexWeightCalculator.setHeavyweightRingBuffer(disruptorManager.getHeavyweightRingBuffer());

        if (dashboardEnabled) {
            com.trading.hf.dashboard.DashboardBridge.start(
                    new VolumeBarGenerator(0, bar -> {}),
                    new SignalEngine(null),
                    new AuctionProfileCalculator(),
                    indexWeightCalculator,
                    optionChainProvider,
                    positionManager,
                    instrumentMaster,
                    virtualPositionManager);
            com.trading.hf.dashboard.DashboardService.setPaperTrading(virtualPositionManager);
        }

        if ("live".equalsIgnoreCase(runMode)) {
            logger.info("Starting application in LIVE mode.");
            String accessToken = ConfigLoader.getProperty("upstox.access.token");
            if (accessToken == null || "YOUR_ACCESS_TOKEN_HERE".equals(accessToken)) {
                System.err.println("FATAL: upstox.access.token is not configured in config.properties.");
                return;
            }

            Set<String> initialInstrumentKeys = new HashSet<>();
            String niftyIndexKey = autoInstrumentManager.getNiftyIndexKey();
            if (niftyIndexKey != null) initialInstrumentKeys.add(niftyIndexKey);

            String niftyFutureKey = autoInstrumentManager.getNiftyFutureKey();
            if (niftyFutureKey != null) initialInstrumentKeys.add(niftyFutureKey);

            Map<String, String> equities = (Map<String, String>) autoInstrumentManager.getMappedKeys().get("equities");
            if (equities != null) {
                for (Map.Entry<String, String> entry : equities.entrySet()) {
                    initialInstrumentKeys.add(entry.getValue());
                    instrumentMaster.addInstrumentKey(entry.getKey(), entry.getValue());
                }
            }

            List<String> optionKeys = (List<String>) autoInstrumentManager.getMappedKeys().get("nifty_options");
            if (optionKeys != null) initialInstrumentKeys.addAll(optionKeys);

            initialInstrumentKeys.add("NSE_INDEX|Nifty Bank");

            if (!initialInstrumentKeys.contains("NSE_INDEX|Nifty Bank")) {
                instrumentMaster.findNearestExpiry("NSE_INDEX|Nifty Bank", java.time.LocalDate.now()).ifPresent(expiry -> {
                    instrumentMaster.findInstrumentKey("NSE_INDEX|Nifty Bank", 0, "FUT", expiry).ifPresent(key -> {
                        initialInstrumentKeys.add(key);
                    });
                });
            }

            initialInstrumentKeys.addAll(indexWeightCalculator.getInstrumentKeys());

            orderManager.setAccessToken(accessToken);

            UpstoxOptionContractService optionContractService = new UpstoxOptionContractService(accessToken);

            UpstoxMarketDataStreamer marketDataStreamer = new UpstoxMarketDataStreamer(
                    accessToken,
                    disruptorManager.getMarketEventRingBuffer(),
                    disruptorManager.getRawFeedRingBuffer(),
                    initialInstrumentKeys);

            DynamicStrikeSubscriber strikeSubscriber = new DynamicStrikeSubscriber(newSubscriptions -> {
                Set<String> currentSubscriptions = new HashSet<>(initialInstrumentKeys);
                Set<String> toSubscribe = newSubscriptions.stream()
                        .filter(s -> !currentSubscriptions.contains(s))
                        .collect(Collectors.toSet());
                Set<String> toUnsubscribe = currentSubscriptions.stream()
                        .filter(s -> !newSubscriptions.contains(s) && !initialInstrumentKeys.contains(s))
                        .collect(Collectors.toSet());

                if (!toSubscribe.isEmpty()) {
                    marketDataStreamer.subscribe(toSubscribe);
                }
                if (!toUnsubscribe.isEmpty()) {
                    marketDataStreamer.unsubscribe(toUnsubscribe);
                }
            }, instrumentMaster, indexInstrumentKey, optionContractService);

            marketDataStreamer.setStrikeSubscriber(strikeSubscriber);
            marketDataStreamer.connect();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                marketDataStreamer.disconnect();
                disruptorManager.shutdown();
                if (questDBWriter != null)
                    questDBWriter.close();
                rawFeedWriter.close();
            }));

        } else {
            logger.info("Starting application in SIMULATION mode.");

            String replaySource = ConfigLoader.getProperty("replay.source", "sample_data");
            IDataReplayer replayer;

            switch (replaySource) {
                case "sample_data":
                    replayer = new MySampleDataReplayer(disruptorManager.getMarketEventRingBuffer(), dataDirectory);
                    break;
                case "questdb":
                    replayer = new QuestDBReplayer(disruptorManager.getMarketEventRingBuffer());
                    break;
                default:
                    System.err.println("FATAL: Unknown replay.source configured: " + replaySource);
                    return;
            }

            replayer.start();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("Simulation finished. Server will remain active for dashboard connection.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                disruptorManager.shutdown();
                if (questDBWriter != null)
                    questDBWriter.close();
            }));

            while (true) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
