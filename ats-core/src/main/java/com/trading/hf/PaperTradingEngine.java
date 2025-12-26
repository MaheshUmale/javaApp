package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PaperTradingEngine implements EventHandler<SignalEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(PaperTradingEngine.class);
    
    private final VirtualPositionManager virtualPositionManager;
    private final Map<String, Double> latestPrices = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int defaultPositionSize;
    private final int maxPositions;
    
    public PaperTradingEngine(VirtualPositionManager virtualPositionManager) {
        this.virtualPositionManager = virtualPositionManager;
        this.enabled = ConfigLoader.getBooleanProperty("paper.trading.enabled", false);
        this.defaultPositionSize = Integer.parseInt(ConfigLoader.getProperty("paper.position.size", "50"));
        this.maxPositions = Integer.parseInt(ConfigLoader.getProperty("paper.max.positions", "5"));
    }
    
    @Override
    public void onEvent(SignalEvent event, long sequence, boolean endOfBatch) {
        if (!enabled) return;
        
        String signalType = event.getType();
        String symbol = event.getSymbol();
        double price = event.getPrice();
        
        logger.info("[PAPER] Signal received: {} for {} at {}", signalType, symbol, price);
        
        // Update latest price for P&L calculation
        latestPrices.put(symbol, price);
        
        // Act on signals
        switch (signalType) {
            case "INITIATIVE_BUY":
            case "STATE_DISCOVERY_UP":
                handleBuySignal(symbol, price, event);
                break;
                
            case "INITIATIVE_SELL":
            case "STATE_DISCOVERY_DOWN":
                handleSellSignal(symbol, price, event);
                break;
                
            case "STATE_ROTATION":
                // Close existing positions on rotation
                closePositions(symbol, price);
                break;
        }
        
        // Update P&L for all open positions
        updatePnL();
    }
    
    private void handleBuySignal(String symbol, double price, SignalEvent event) {
        // Check if we already have a position
        if (virtualPositionManager.hasPosition(symbol)) {
            logger.info("[PAPER] Already in position for {}, skipping BUY", symbol);
            return;
        }
        
        // Check max positions limit
        if (virtualPositionManager.getAllOpenPositions().size() >= maxPositions) {
            logger.info("[PAPER] Max positions ({}) reached, skipping BUY for {}", maxPositions, symbol);
            return;
        }
        
        // Calculate position size
        int quantity = calculatePositionSize(symbol, price);
        
        // Open virtual position
        virtualPositionManager.openPosition(
            symbol,
            quantity,
            "BUY",
            price,
            System.currentTimeMillis(),
            event.getVah(),
            event.getVal(),
            event.getPoc()
        );
        
        logger.info("[PAPER] ✅ BUY {} x{} @ {}", symbol, quantity, price);
    }
    
    private void handleSellSignal(String symbol, double price, SignalEvent event) {
        // Check if we have a long position to exit
        VirtualPosition position = virtualPositionManager.getPosition(symbol);
        
        if (position != null && "BUY".equals(position.getSide())) {
            // Close long position
            closePosition(symbol, price, "SIGNAL_SELL");
        }
        
        // Could also open short positions here if desired
        // For now, we only do long positions
    }
    
    private void closePosition(String symbol, double price, String reason) {
        VirtualPosition position = virtualPositionManager.getPosition(symbol);
        if (position == null) return;
        
        double pnl = virtualPositionManager.closePosition(symbol, price, System.currentTimeMillis(), reason);
        
        logger.info("[PAPER] ❌ CLOSE {} | Entry: {} | Exit: {} | P&L: {}", 
            symbol, position.getEntryPrice(), price, pnl);
    }
    
    private void closePositions(String symbol, double price) {
        if (virtualPositionManager.hasPosition(symbol)) {
            closePosition(symbol, price, "STATE_ROTATION");
        }
    }
    
    private void updatePnL() {
        for (VirtualPosition position : virtualPositionManager.getAllOpenPositions()) {
            Double currentPrice = latestPrices.get(position.getInstrumentKey());
            if (currentPrice != null) {
                position.updateCurrentPrice(currentPrice);
            }
        }
    }
    
    private int calculatePositionSize(String symbol, double price) {
        // Simple fixed size for now
        // Could be enhanced with: % of capital, volatility-based, etc.
        return defaultPositionSize;
    }
    
    public VirtualPositionManager getVirtualPositionManager() {
        return virtualPositionManager;
    }
}
