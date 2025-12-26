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

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // --- Configuration ---
        String runMode = ConfigLoader.getProperty("run.mode", "live");
        
        // Command line override: java -jar app.jar simulation
        if (args.length > 0) {
            runMode = args[0];
            logger.info("Mode overridden by command line argument: {}", runMode);
        }

        boolean questDbEnabled = ConfigLoader.getBooleanProperty("questdb.enabled", false);
        long volumeThreshold = ConfigLoader.getLongProperty("volume.bar.threshold", 100);
        String dataDirectory = "data";

        // --- Initialization ---
        boolean dashboardEnabled = ConfigLoader.getBooleanProperty("dashboard.enabled", true);
        QuestDBWriter questDBWriter = questDbEnabled ? new QuestDBWriter() : null;
        RawFeedWriter rawFeedWriter = new RawFeedWriter();

        AuctionProfileCalculator auctionProfileCalculator = new AuctionProfileCalculator();
        SignalEngine signalEngine = new SignalEngine(auctionProfileCalculator);
        
        // --- Daily Instrument Mapping ---
        InstrumentLoader loader = new InstrumentLoader("instruments.db", "NSE.JSON.gz", "NSE.json");
        AutoInstrumentManager autoInstrumentManager = new AutoInstrumentManager(loader, "mapped_instruments.json");
        autoInstrumentManager.initialize();
        DashboardBridge.setNiftyFutureKey(autoInstrumentManager.getNiftyFutureKey());

        InstrumentMaster instrumentMaster = new InstrumentMaster("instrument-master.json");
        IndexWeightCalculator indexWeightCalculator = new IndexWeightCalculator("IndexWeights.json", instrumentMaster);
        OptionChainProvider optionChainProvider = new OptionChainProvider(instrumentMaster);
        PositionManager positionManager = new PositionManager();
        UpstoxOrderManager orderManager = new UpstoxOrderManager(null, positionManager); // Access token will be set if live
        ThetaExitGuard thetaExitGuard = new ThetaExitGuard(positionManager, orderManager);

        SignalPersistenceWriter signalPersistenceWriter = questDbEnabled ? new SignalPersistenceWriter() : null;
        OrderPersistenceWriter orderPersistenceWriter = questDbEnabled ? new OrderPersistenceWriter() : null;
        TelemetryWriter telemetryWriter = questDbEnabled ? new TelemetryWriter() : null;
        HeavyweightWriter heavyweightWriter = questDbEnabled ? new HeavyweightWriter() : null;

        VolumeBarGenerator volumeBarGenerator = new VolumeBarGenerator(volumeThreshold, bar -> {
            auctionProfileCalculator.onVolumeBar(bar);
            signalEngine.onVolumeBar(bar);
            String friendlySymbol = instrumentMaster.getInstrument(bar.getSymbol())
                    .map(InstrumentMaster.InstrumentDefinition::getTradingSymbol)
                    .orElse(bar.getSymbol());
            logger.info("New Volume Bar: {} ({}) | O: {} H: {} L: {} C: {} V: {}",
                    bar.getSymbol(), friendlySymbol, bar.getOpen(), bar.getHigh(), bar.getLow(), bar.getClose(), bar.getVolume());
        });

        // Paper Trading Setup
        VirtualPositionManager virtualPositionManager = new VirtualPositionManager();
        PaperTradingEngine paperTradingEngine = new PaperTradingEngine(virtualPositionManager);

        DisruptorManager disruptorManager = new DisruptorManager(
                questDBWriter,
                rawFeedWriter,
                volumeBarGenerator,
                indexWeightCalculator,
                optionChainProvider,
                thetaExitGuard,
                signalPersistenceWriter,
                orderPersistenceWriter,
                telemetryWriter,
                heavyweightWriter,
                List.of((event, seq, end) -> DashboardBridge.onMarketEvent(event)),
                paperTradingEngine);
        
        signalEngine.setSignalRingBuffer(disruptorManager.getSignalRingBuffer());
        orderManager.setOrderRingBuffer(disruptorManager.getOrderRingBuffer());
        indexWeightCalculator.setHeavyweightRingBuffer(disruptorManager.getHeavyweightRingBuffer());

        if (dashboardEnabled) {
            com.trading.hf.dashboard.DashboardBridge.start(
                    volumeBarGenerator,
                    signalEngine,
                    auctionProfileCalculator,
                    indexWeightCalculator,
                    optionChainProvider,
                    positionManager,
                    instrumentMaster,
                    virtualPositionManager);
            com.trading.hf.dashboard.DashboardService.setPaperTrading(virtualPositionManager);
        }

        if ("live".equalsIgnoreCase(runMode)) {
            // --- Live Mode ---
            logger.info("Starting application in LIVE mode.");
            String accessToken = ConfigLoader.getProperty("upstox.access.token");
            if (accessToken == null || "YOUR_ACCESS_TOKEN_HERE".equals(accessToken)) {
                System.err.println("FATAL: upstox.access.token is not configured in config.properties.");
                return;
            }

            Set<String> initialInstrumentKeys = new HashSet<>();
            
            // Add Nifty Index and Future from mapping
            String niftyIndexKey = autoInstrumentManager.getNiftyIndexKey();
            if (niftyIndexKey != null) initialInstrumentKeys.add(niftyIndexKey);
            
            String niftyFutureKey = autoInstrumentManager.getNiftyFutureKey();
            if (niftyFutureKey != null) initialInstrumentKeys.add(niftyFutureKey);
            
            // Add Equities from mapping
            Map<String, String> equities = (Map<String, String>) autoInstrumentManager.getMappedKeys().get("equities");
            if (equities != null) {
                for (Map.Entry<String, String> entry : equities.entrySet()) {
                    initialInstrumentKeys.add(entry.getValue());
                    instrumentMaster.addInstrumentKey(entry.getKey(), entry.getValue());
                }
            }

            // Add Nifty Options from mapping
            List<String> optionKeys = (List<String>) autoInstrumentManager.getMappedKeys().get("nifty_options");
            if (optionKeys != null) initialInstrumentKeys.addAll(optionKeys);

            initialInstrumentKeys.add("NSE_INDEX|Nifty Bank");

            // Dynamically find the near-month Bank Nifty Future (as backup)
            if (!initialInstrumentKeys.contains("NSE_INDEX|Nifty Bank")) {
                instrumentMaster.findNearestExpiry("NSE_INDEX|Nifty Bank", java.time.LocalDate.now()).ifPresent(expiry -> {
                    instrumentMaster.findInstrumentKey("NSE_INDEX|Nifty Bank", 0, "FUT", expiry).ifPresent(key -> {
                        initialInstrumentKeys.add(key);
                        logger.info("Dynamically added Bank Nifty Future: {}", key);
                    });
                });
            }

            // For now, let's just make sure we add the heavyweights if not already present
            initialInstrumentKeys.addAll(indexWeightCalculator.getInstrumentKeys());

            orderManager.setAccessToken(accessToken); // Update order manager with token

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
                    logger.info("Subscribing to: {}", toSubscribe);
                }
                if (!toUnsubscribe.isEmpty()) {
                    marketDataStreamer.unsubscribe(toUnsubscribe);
                    logger.info("Unsubscribing from: {}", toUnsubscribe);
                }
            }, instrumentMaster, "NSE_INDEX|Nifty 50", optionContractService);

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
            // --- Simulation Mode ---
            logger.info("Starting application in SIMULATION mode.");

            String replaySource = ConfigLoader.getProperty("replay.source", "questdb");
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

            replayer.start(); // This will block until replay is complete

            try {
                // Give logs a moment to flush before shutting down
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            logger.info("Simulation finished. Server will remain active for dashboard connection.");

            // Keep the main thread alive to allow the dashboard to be viewed.
            // The shutdown hook will handle closing resources.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown hook initiated.");
                disruptorManager.shutdown();
                if (questDBWriter != null)
                    questDBWriter.close();
                logger.info("Resources released.");
            }));

            while (true) {
                try {
                    Thread.sleep(10000); // Sleep indefinitely
                } catch (InterruptedException e) {
                    logger.warn("Main thread interrupted, shutting down.");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
