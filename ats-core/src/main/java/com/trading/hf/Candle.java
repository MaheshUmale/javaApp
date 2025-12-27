package com.trading.hf;

public class Candle {
    private final long timestamp;
    private final double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public Candle(long timestamp, double open, double high, double low, double close, long volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public void update(double price, long volume) {
        if (price > this.high) {
            this.high = price;
        }
        if (price < this.low) {
            this.low = price;
        }
        this.close = price;
        this.volume += volume;
    }

    public long getTimestamp() {
        return timestamp;
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
}
