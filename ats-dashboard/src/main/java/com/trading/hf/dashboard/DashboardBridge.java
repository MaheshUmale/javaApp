package com.trading.hf.dashboard;

import com.google.gson.Gson;
import com.trading.hf.*;
import com.trading.hf.MarketEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DashboardBridge {
    private static final Logger logger = LoggerFactory.getLogger(DashboardBridge.class);
    private static final Gson gson = new Gson();
    private static DashboardService dashboardService = null;
    private static final Object lock = new Object();

    private static volatile double latestSpotPrice = 0.0;
    private static volatile double latestFuturePrice = 0.0;
    private static volatile double indexOpen = 0.0;
    private static volatile String niftyFutureKey = null;

    // Segregated persistence for Stock vs Option
    private static volatile String lastActiveStockKey = "NSE_INDEX|Nifty 50";
    private static volatile double latestStockLtp = 0.0;
    private static volatile String focalOptionKey = null;
    private static volatile double latestOptionLtp = 0.0;

    private static InstrumentMaster instrumentMaster = null;
    private static VirtualPositionManager virtualPositionManager = null;

    public static void start(
            VolumeBarGenerator volumeBarGenerator,
            SignalEngine signalEngine,
            AuctionProfileCalculator auctionProfileCalculator,
            IndexWeightCalculator indexWeightCalculator,
            OptionChainProvider optionChainProvider,
            PositionManager positionManager,
            InstrumentMaster instrumentMaster,
            VirtualPositionManager virtualPositionManager) {
        synchronized (lock) {
            if (dashboardService == null) {
                DashboardBridge.instrumentMaster = instrumentMaster;
                DashboardBridge.virtualPositionManager = virtualPositionManager;
                dashboardService = new DashboardService();
                dashboardService.start();

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "Dashboard-Snapshot-Sender");
                    t.setDaemon(true);
                    return t;
                });

                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        sendSnapshot(volumeBarGenerator.getLastBar(), signalEngine, auctionProfileCalculator,
                                indexWeightCalculator, optionChainProvider, positionManager);
                    } catch (Exception e) {
                    }
                }, 1, 1, TimeUnit.SECONDS);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    scheduler.shutdown();
                    dashboardService.stop();
                }));
            }
        }

        volumeBarGenerator.setDashboardConsumer(volumeBar -> {
            sendSnapshot(volumeBar, signalEngine, auctionProfileCalculator, indexWeightCalculator, optionChainProvider,
                    positionManager);
        });
    }

    public static void setNiftyFutureKey(String key) {
        niftyFutureKey = key;
    }

    public static void setDashboardService(DashboardService service) {
        dashboardService = service;
    }

    public static void onMarketEvent(MarketEvent event) {
        if (event == null)
            return;
        String symbol = event.getSymbol();
        if ("NSE_INDEX|Nifty 50".equals(symbol) || "NIFTY 50".equals(symbol)) {
            latestSpotPrice = event.getLtp();
            if (indexOpen == 0.0)
                indexOpen = event.getDayOpen();
        } else if (symbol.equals(niftyFutureKey)) {
            latestFuturePrice = event.getLtp();
        }

        // Stability Fix: Update option LTP ONLY if it matches the current focused
        // instrument
        if (focalOptionKey != null && focalOptionKey.equals(symbol)) {
            latestOptionLtp = event.getLtp();
        } else if (focalOptionKey == null && isDerivative(symbol)) {
            // Pick first option encountered as initial focus
            focalOptionKey = symbol;
            latestOptionLtp = event.getLtp();
        }

        // Live P&L update for Paper Trading (updates even if heartbeat/snapshot hasn't
        // fired)
        if (virtualPositionManager != null && isDerivative(symbol)) {
            VirtualPosition pos = virtualPositionManager.getPosition(symbol);
            if (pos != null) {
                pos.updateCurrentPrice(event.getLtp());
            }
        }
    }

    private static boolean isDerivative(String key) {
        if (key == null)
            return false;
        if (key.startsWith("NSE_FO|"))
            return true;

        // Check friendly name for simulation safety
        String friendly = getFriendlyName(key);
        if (friendly.contains(" CE") || friendly.contains(" PE") || friendly.contains("|24")
                || friendly.contains("|25"))
            return true;

        if (instrumentMaster != null) {
            return instrumentMaster.getInstrument(key)
                    .map(it -> "NSE_FO".equalsIgnoreCase(it.getSegment()))
                    .orElse(false);
        }
        return false;
    }

    private static String getFriendlyName(String key) {
        if (key == null)
            return "---";
        if (instrumentMaster != null) {
            return instrumentMaster.getInstrument(key)
                    .map(InstrumentMaster.InstrumentDefinition::getTradingSymbol)
                    .orElse(key);
        }
        return key;
    }

    private static void sendSnapshot(
            VolumeBar volumeBar,
            SignalEngine signalEngine,
            AuctionProfileCalculator auctionProfileCalculator,
            IndexWeightCalculator indexWeightCalculator,
            OptionChainProvider optionChainProvider,
            PositionManager positionManager) {
        DashboardViewModel viewModel = new DashboardViewModel();

        // 1. Header
        viewModel.timestamp = System.currentTimeMillis() / 1000;
        viewModel.indexSpot = latestSpotPrice;
        viewModel.indexFuture = (latestFuturePrice > 0) ? latestFuturePrice : latestSpotPrice;
        viewModel.indexBasis = (latestFuturePrice > 0) ? (latestFuturePrice - latestSpotPrice) : 0.0;
        viewModel.indexChange = (indexOpen > 0) ? (latestSpotPrice - indexOpen) : 0.0;
        viewModel.pcr = optionChainProvider.getPcr();

        // 2. STABLE Focal Tracking (No auto-switching on every bar)
        // We maintain persistent focus. LTP updates ONLY if the bar matches current
        // focus.
        if (volumeBar != null) {
            String sym = volumeBar.getSymbol();
            boolean isDerivative = isDerivative(sym);

            if (isDerivative) {
                // STICKY FOCUS: Only switch focus if we don't have an active trade in the
                // current focal option
                boolean currentFocalHasTrade = (virtualPositionManager != null && focalOptionKey != null
                        && virtualPositionManager.hasPosition(focalOptionKey));
                boolean newInstrumentHasTrade = (virtualPositionManager != null
                        && virtualPositionManager.hasPosition(sym));

                if (!currentFocalHasTrade || newInstrumentHasTrade) {
                    focalOptionKey = sym;
                    latestOptionLtp = volumeBar.getClose();
                }
            } else {
                // Update stock LTP only if this bar matches our stock focus
                if (sym.equals(lastActiveStockKey)) {
                    latestStockLtp = volumeBar.getClose();
                }
                // FIX: Lock to Index. Do NOT allow equity stocks to hijack the profile view.
                // else if ("NSE_INDEX|Nifty 50".equals(lastActiveStockKey) &&
                // sym.startsWith("NSE_EQ|")) {
                // lastActiveStockKey = sym;
                // latestStockLtp = volumeBar.getClose();
                // }
            }
        }

        // Populate Stock focal (for Auction/Profile)
        viewModel.stockKey = lastActiveStockKey;
        viewModel.stockSymbol = getFriendlyName(lastActiveStockKey);

        // STABILITY FIX: Don't flip-flop between Spot and Stock LTP.
        // If the focus is Nifty Index, use latestSpotPrice. Otherwise use
        // latestStockLtp.
        if ("NSE_INDEX|Nifty 50".equals(lastActiveStockKey) || "NIFTY 50".equals(lastActiveStockKey)) {
            viewModel.stockLtp = latestSpotPrice;
        } else {
            viewModel.stockLtp = (latestStockLtp > 0) ? latestStockLtp : latestSpotPrice;
        }

        // Populate Option focal (for Trade Panel)
        if (focalOptionKey != null) {
            viewModel.optionKey = focalOptionKey;
            viewModel.optionSymbol = getFriendlyName(focalOptionKey);
            viewModel.optionLtp = latestOptionLtp;
        } else {
            viewModel.optionKey = null;
            viewModel.optionSymbol = "---";
            viewModel.optionLtp = 0.0;
        }

        // Metrics
        viewModel.wssLatency = 15;
        viewModel.questDbLag = 0;
        viewModel.disruptor = 0.5;

        // 3. Auction Profile (Always relative to stockFocus)
        String profileSymbol = lastActiveStockKey;
        logger.debug("Fetching auction profile for symbol: {}", profileSymbol);
        AuctionProfileCalculator.MarketProfile profile = auctionProfileCalculator.getProfile(profileSymbol);
        if (profile == null && !profileSymbol.equals("NSE_INDEX|Nifty 50")) {
            logger.debug("Profile null for {}, trying NSE_INDEX|Nifty 50", profileSymbol);
            profile = auctionProfileCalculator.getProfile("NSE_INDEX|Nifty 50");
        }

        if (profile != null) {
            viewModel.auctionProfile = new DashboardViewModel.MarketProfileViewModel();
            viewModel.auctionProfile.vah = profile.getVah();
            viewModel.auctionProfile.val = profile.getVal();
            viewModel.auctionProfile.poc = profile.getPoc();
            viewModel.totalVol = String.format("%.1fM", (double) profile.getTotalVolume() / 1_000_000.0);
            logger.debug("Auction profile populated: VAH={}, POC={}, VAL={}", 
                profile.getVah(), profile.getPoc(), profile.getVal());
        } else {
            logger.warn("Auction profile is NULL for both {} and NSE_INDEX|Nifty 50", profileSymbol);
            viewModel.totalVol = "0.0M";
            // CRITICAL FIX: Initialize empty profile instead of leaving it null
            viewModel.auctionProfile = new DashboardViewModel.MarketProfileViewModel();
            viewModel.auctionProfile.vah = 0.0;
            viewModel.auctionProfile.val = 0.0;
            viewModel.auctionProfile.poc = 0.0;
        }

        // 4. Heavyweights
        viewModel.heavyweights = indexWeightCalculator.getHeavyweights().values().stream()
                .map(hw -> {
                    DashboardViewModel.HeavyweightViewModel hwvm = new DashboardViewModel.HeavyweightViewModel();
                    hwvm.rank = hw.getRank();
                    hwvm.name = hw.getName();
                    hwvm.companyName = hw.getCompanyName();
                    hwvm.weight = String.format("%.2f%%", hw.getWeight());
                    hwvm.delta = hw.getDelta();
                    hwvm.sector = hw.getSector();
                    // FIX: Use actual LTP or show "N/A" instead of mock prices
                    if (hw.getLtp() > 0) {
                        hwvm.priceChange = String.format("%.2f (0.0%%)", hw.getLtp());
                    } else {
                        hwvm.priceChange = "N/A";
                    }
                    hwvm.qtp = 0;
                    return hwvm;
                })
                .sorted((a, b) -> Integer.compare(a.rank, b.rank))
                .collect(Collectors.toList());
        viewModel.aggregateWeightedDelta = indexWeightCalculator.getAggregateWeightedDelta();

        // 5. Option Chain
        viewModel.option_window = optionChainProvider.getOptionChainWindow().stream()
                .map(dto -> {
                    DashboardViewModel.OptionViewModel ovm = new DashboardViewModel.OptionViewModel();
                    ovm.strike = dto.getStrike();
                    ovm.type = dto.getType();
                    ovm.ltp = dto.getLtp();
                    ovm.oi = dto.getOi();
                    ovm.oi_chg = dto.getOiChangePercent();
                    ovm.sentiment = dto.getSentiment();
                    return ovm;
                })
                .collect(Collectors.toList());

        // 6. Alert & State
        viewModel.auctionState = (volumeBar != null) ? signalEngine.getAuctionState(volumeBar.getSymbol()).toString()
                : "ROTATION";
        viewModel.alerts = new ArrayList<>();
        DashboardViewModel.AlertViewModel a1 = new DashboardViewModel.AlertViewModel();
        a1.type = "success";
        a1.message = "Put OI @ " + ((int) (latestSpotPrice / 100) * 100) + " Spiking (+5.2%)";
        viewModel.alerts.add(a1);

        // 7. Active Trades (Show BOTH real and virtual positions)
        List<DashboardViewModel.TradeViewModel> allTrades = new ArrayList<>();

        // Real positions
        try {
            for (Position pos : positionManager.getAllPositions().values()) {
                DashboardViewModel.TradeViewModel tvm = new DashboardViewModel.TradeViewModel();
                tvm.symbol = getFriendlyName(pos.getInstrumentKey());
                tvm.entry = pos.getEntryPrice();
                tvm.ltp = pos.getEntryPrice();
                tvm.qty = pos.getQuantity();
                tvm.pnl = 0.0;
                tvm.reason = pos.getSide() + " (REAL)";
                allTrades.add(tvm);
            }
        } catch (Exception e) {
            logger.error("Error adding real trades to view model", e);
        }

        // Virtual (paper) positions
        try {
            if (virtualPositionManager != null) {
                for (VirtualPosition pos : virtualPositionManager.getAllOpenPositions()) {
                    DashboardViewModel.TradeViewModel tvm = new DashboardViewModel.TradeViewModel();
                    tvm.symbol = getFriendlyName(pos.getInstrumentKey());
                    tvm.entry = pos.getEntryPrice();
                    tvm.ltp = pos.getCurrentPrice();
                    tvm.qty = pos.getQuantity();
                    tvm.pnl = pos.getUnrealizedPnL();
                    tvm.reason = pos.getSide() + " (PAPER)";
                    allTrades.add(tvm);
                }
            }
        } catch (Exception e) {
            logger.error("Error adding virtual trades to view model", e);
        }

        viewModel.activeTrades = allTrades;
        if (viewModel.activeTrades.isEmpty()) {
            // FIX: Show no trades instead of fake mock trades
        }

        // 8. OHLCP (For Trade Panel chart)
        viewModel.theta_gcr = 1.22;
        viewModel.ohlc = new DashboardViewModel.OhlcViewModel();
        // Also populate volumeBar for the main chart consumer
        viewModel.volumeBar = new DashboardViewModel.VolumeBarViewModel();

        // Check if the current volume bar is for an option derivative
        if (volumeBar != null && isDerivative(volumeBar.getSymbol())) {
            // We have a real option bar with proper OHLC
            viewModel.ohlc.open = volumeBar.getOpen();
            viewModel.ohlc.high = volumeBar.getHigh();
            viewModel.ohlc.low = volumeBar.getLow();
            viewModel.ohlc.close = volumeBar.getClose();

            // Mirror into volumeBar for frontend compatibility
            viewModel.volumeBar.orderBookImbalance = volumeBar.getOrderBookImbalance();
            viewModel.volumeBar.startTime = volumeBar.getStartTime();
            viewModel.volumeBar.open = volumeBar.getOpen();
            viewModel.volumeBar.high = volumeBar.getHigh();
            viewModel.volumeBar.low = volumeBar.getLow();
            viewModel.volumeBar.close = volumeBar.getClose();

            // Update the display metadata for the Trade Panel
            viewModel.optionSymbol = getFriendlyName(volumeBar.getSymbol());
            viewModel.optionLtp = volumeBar.getClose();
        } else {
            // No option bar available - create a flat bar to avoid dashboard errors
            // Use the tracked option LTP if available, otherwise use spot price
            double price = (latestOptionLtp > 0) ? latestOptionLtp : latestSpotPrice;

            // Create a flat OHLC (will display as a dash in the chart until real data
            // arrives)
            viewModel.ohlc.open = price;
            viewModel.ohlc.high = price;
            viewModel.ohlc.low = price;
            viewModel.ohlc.close = price;

            // Provide a flat volumeBar as well to keep charts stable
            viewModel.volumeBar.orderBookImbalance = 0.0;
            viewModel.volumeBar.startTime = System.currentTimeMillis();
            viewModel.volumeBar.open = price;
            viewModel.volumeBar.high = price;
            viewModel.volumeBar.low = price;
            viewModel.volumeBar.close = price;

            // Update symbol display
            if (focalOptionKey != null && latestOptionLtp > 0) {
                viewModel.optionSymbol = getFriendlyName(focalOptionKey);
                viewModel.optionLtp = latestOptionLtp;
            } else {
                viewModel.optionSymbol = "---";
                viewModel.optionLtp = 0.0;
            }
        }

        String json = gson.toJson(viewModel);
        if (dashboardService != null)
            dashboardService.broadcast(json);
    }
}
