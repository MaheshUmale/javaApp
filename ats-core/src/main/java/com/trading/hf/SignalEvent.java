package com.trading.hf;

import com.lmax.disruptor.EventFactory;

public class SignalEvent {
    private String symbol;
    private String type;
    private double price;
    private double vah;
    private double val;
    private double poc;
    private double delta;
    private long timestamp;

    public void set(String symbol, String type, double price, double vah, double val, double poc, double delta, long timestamp) {
        this.symbol = symbol;
        this.type = type;
        this.price = price;
        this.vah = vah;
        this.val = val;
        this.poc = poc;
        this.delta = delta;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public String getType() { return type; }
    public double getPrice() { return price; }
    public double getVah() { return vah; }
    public double getVal() { return val; }
    public double getPoc() { return poc; }
    public double getDelta() { return delta; }
    public long getTimestamp() { return timestamp; }

    public static final EventFactory<SignalEvent> EVENT_FACTORY = SignalEvent::new;
}
