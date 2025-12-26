package com.trading.hf;

public class VirtualTrade {
    private final String instrumentKey;
    private final int quantity;
    private final String side;
    private final double entryPrice;
    private final long entryTime;
    private final double exitPrice;
    private final long exitTime;
    private final double pnl;
    private final String exitReason;
    private final double vah, val, poc;
    
    public VirtualTrade(VirtualPosition position, double exitPrice, 
                       long exitTime, double pnl, String exitReason) {
        this.instrumentKey = position.getInstrumentKey();
        this.quantity = position.getQuantity();
        this.side = position.getSide();
        this.entryPrice = position.getEntryPrice();
        this.entryTime = position.getEntryTime();
        this.exitPrice = exitPrice;
        this.exitTime = exitTime;
        this.pnl = pnl;
        this.exitReason = exitReason;
        this.vah = position.getVah();
        this.val = position.getVal();
        this.poc = position.getPoc();
    }
    
    public boolean isWinner() { return pnl > 0; }
    public long getHoldingPeriod() { return exitTime - entryTime; }
    
    // Getters
    public String getInstrumentKey() { return instrumentKey; }
    public int getQuantity() { return quantity; }
    public String getSide() { return side; }
    public double getEntryPrice() { return entryPrice; }
    public double getExitPrice() { return exitPrice; }
    public double getPnl() { return pnl; }
    public String getExitReason() { return exitReason; }
    public long getEntryTime() { return entryTime; }
    public long getExitTime() { return exitTime; }
}
