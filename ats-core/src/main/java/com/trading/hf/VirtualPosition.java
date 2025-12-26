package com.trading.hf;

public class VirtualPosition {
    private final String instrumentKey;
    private final int quantity;
    private final String side;  // "BUY" or "SELL"
    private final double entryPrice;
    private final long entryTime;
    private final double vah;
    private final double val;
    private final double poc;
    
    private double currentPrice;
    private double unrealizedPnL;
    
    public VirtualPosition(String instrumentKey, int quantity, String side,
                          double entryPrice, long entryTime,
                          double vah, double val, double poc) {
        this.instrumentKey = instrumentKey;
        this.quantity = quantity;
        this.side = side;
        this.entryPrice = entryPrice;
        this.entryTime = entryTime;
        this.vah = vah;
        this.val = val;
        this.poc = poc;
        this.currentPrice = entryPrice;
        this.unrealizedPnL = 0.0;
    }
    
    public void updateCurrentPrice(double price) {
        this.currentPrice = price;
        double diff = price - entryPrice;
        if ("SELL".equals(side)) {
            diff = -diff;
        }
        this.unrealizedPnL = diff * quantity;
    }
    
    // Getters
    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public long getEntryTime() { return entryTime; }
    public double getCurrentPrice() { return currentPrice; }
    public double getUnrealizedPnL() { return unrealizedPnL; }
    public double getVah() { return vah; }
    public double getVal() { return val; }
    public double getPoc() { return poc; }
}
