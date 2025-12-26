package com.trading.hf;

public class VolumeBar {
    private final String symbol;
    private final long startTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private double vwap;
    private double cumulativeVolumeDelta;
    private double orderBookImbalance;

    public VolumeBar(String symbol, long startTime, double price, long volume) {
        this.symbol = symbol;
        this.startTime = startTime;
        this.open = price;
        this.high = price;
        this.low = price;
        this.close = price;
        this.volume = volume;
        this.vwap = price;
    }

    public void addTick(double price, long tickVolume, int side) {
        this.high = Math.max(this.high, price);
        this.low = Math.min(this.low, price);
        this.close = price;

        long newTotalVolume = this.volume + tickVolume;
        this.vwap = ((this.vwap * this.volume) + (price * tickVolume)) / newTotalVolume;
        this.volume = newTotalVolume;

        // side == 1 for buy, -1 for sell
        this.cumulativeVolumeDelta += (tickVolume * side);
    }

    public String getSymbol() {
        return symbol;
    }

    public long getStartTime() {
        return startTime;
    }

    public double getOpen() {
        return open;
    }

    public double getHigh() {
        return high;
    }

    public double getLow() {
        return low;
    }

    public double getClose() {
        return close;
    }

    public long getVolume() {
        return volume;
    }

    public double getVwap() {
        return vwap;
    }

    public double getCumulativeVolumeDelta() {
        return cumulativeVolumeDelta;
    }

    public double getOrderBookImbalance() {
        return orderBookImbalance;
    }

    public void setOrderBookImbalance(double orderBookImbalance) {
        this.orderBookImbalance = orderBookImbalance;
    }
}
