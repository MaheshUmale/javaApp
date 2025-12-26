package com.trading.hf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualPositionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VirtualPositionManager.class);
    
    private final ConcurrentHashMap<String, VirtualPosition> openPositions = new ConcurrentHashMap<>();
    private final List<VirtualTrade> closedTrades = new ArrayList<>();
    private final PerformanceTracker performanceTracker = new PerformanceTracker();
    
    public void openPosition(String instrumentKey, int quantity, String side, 
                            double entryPrice, long entryTime,
                            double vah, double val, double poc) {
        VirtualPosition position = new VirtualPosition(
            instrumentKey, quantity, side, entryPrice, entryTime, vah, val, poc
        );
        openPositions.put(instrumentKey, position);
        
        logger.info("[VIRTUAL] Opened {} position: {} x{} @ {}", 
            side, instrumentKey, quantity, entryPrice);
    }
    
    public double closePosition(String instrumentKey, double exitPrice, long exitTime, String exitReason) {
        VirtualPosition position = openPositions.remove(instrumentKey);
        if (position == null) {
            logger.warn("[VIRTUAL] No position found for {}", instrumentKey);
            return 0.0;
        }
        
        // Calculate P&L
        double pnl = calculatePnL(position, exitPrice);
        
        // Create closed trade record
        VirtualTrade trade = new VirtualTrade(
            position,
            exitPrice,
            exitTime,
            pnl,
            exitReason
        );
        
        synchronized (closedTrades) {
            closedTrades.add(trade);
        }
        
        // Update performance tracker
        performanceTracker.recordTrade(trade);
        
        logger.info("[VIRTUAL] Closed position: {} | P&L: {} | Reason: {}", 
            instrumentKey, pnl, exitReason);
        
        return pnl;
    }
    
    private double calculatePnL(VirtualPosition position, double exitPrice) {
        double diff = exitPrice - position.getEntryPrice();
        if ("SELL".equals(position.getSide())) {
            diff = -diff;  // Invert for short positions
        }
        return diff * position.getQuantity();
    }
    
    public boolean hasPosition(String instrumentKey) {
        return openPositions.containsKey(instrumentKey);
    }
    
    public VirtualPosition getPosition(String instrumentKey) {
        return openPositions.get(instrumentKey);
    }
    
    public Collection<VirtualPosition> getAllOpenPositions() {
        return openPositions.values();
    }
    
    public List<VirtualTrade> getAllClosedTrades() {
        synchronized (closedTrades) {
            return new ArrayList<>(closedTrades);
        }
    }
    
    public PerformanceTracker getPerformanceTracker() {
        return performanceTracker;
    }
}
