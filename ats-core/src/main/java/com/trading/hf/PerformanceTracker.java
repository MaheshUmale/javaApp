package com.trading.hf;

import java.util.ArrayList;
import java.util.List;

public class PerformanceTracker {
    private final List<VirtualTrade> trades = new ArrayList<>();
    private double totalPnL = 0.0;
    private int winners = 0;
    private int losers = 0;
    
    public synchronized void recordTrade(VirtualTrade trade) {
        trades.add(trade);
        totalPnL += trade.getPnl();
        if (trade.isWinner()) {
            winners++;
        } else {
            losers++;
        }
    }
    
    public double getTotalPnL() { return totalPnL; }
    public int getTotalTrades() { return trades.size(); }
    public int getWinners() { return winners; }
    public int getLosers() { return losers; }
    public double getWinRate() { 
        return trades.isEmpty() ? 0.0 : (double) winners / trades.size() * 100;
    }
    
    public double getAveragePnL() {
        return trades.isEmpty() ? 0.0 : totalPnL / trades.size();
    }
    
    public double getMaxWin() {
        return trades.stream()
            .filter(VirtualTrade::isWinner)
            .mapToDouble(VirtualTrade::getPnl)
            .max()
            .orElse(0.0);
    }
    
    public double getMaxLoss() {
        return trades.stream()
            .filter(t -> !t.isWinner())
            .mapToDouble(VirtualTrade::getPnl)
            .min()
            .orElse(0.0);
    }
    
    public List<VirtualTrade> getAllTrades() {
        return new ArrayList<>(trades);
    }
}
