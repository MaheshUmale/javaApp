package com.trading.hf;

import com.lmax.disruptor.EventFactory;

public class HeavyweightEvent {
    private String symbol;
    private double price;
    private double weight;
    private double delta;
    private double aggregateDelta;
    private long timestamp;

    public void set(String symbol, double price, double weight, double delta, double aggregateDelta, long timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.weight = weight;
        this.delta = delta;
        this.aggregateDelta = aggregateDelta;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public double getWeight() { return weight; }
    public double getDelta() { return delta; }
    public double getAggregateDelta() { return aggregateDelta; }
    public long getTimestamp() { return timestamp; }

    public static final EventFactory<HeavyweightEvent> EVENT_FACTORY = HeavyweightEvent::new;
}
