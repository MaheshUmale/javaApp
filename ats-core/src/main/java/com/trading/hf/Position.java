package com.trading.hf;

public class Position {

    private final String instrumentKey;
    private final int quantity;
    private final String side;
    private final double entryPrice;
    private final long entryTimestamp;

    public Position(String instrumentKey, int quantity, String side, double entryPrice, long entryTimestamp) {
        this.instrumentKey = instrumentKey;
        this.quantity = quantity;
        this.side = side;
        this.entryPrice = entryPrice;
        this.entryTimestamp = entryTimestamp;
    }

    public String getInstrumentKey() {
        return instrumentKey;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getSide() {
        return side;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public long getEntryTimestamp() {
        return entryTimestamp;
    }
}
