package com.trading.hf;

import java.util.List;

public class RawFeedEvent {

    private String instrumentKey;
    private long timestamp;
    private double ltp;
    private long ltq;
    private List<BookEntry> bids;
    private List<BookEntry> asks;
    private double bestBid;
    private double bestAsk;

    public RawFeedEvent() {
        // Default constructor for Disruptor
    }

    public static class BookEntry {
        private final double price;
        private final long quantity;
        private final long orders;

        public BookEntry(double price, long quantity, long orders) {
            this.price = price;
            this.quantity = quantity;
            this.orders = orders;
        }

        public double getPrice() {
            return price;
        }

        public long getQuantity() {
            return quantity;
        }

        public long getOrders() {
            return orders;
        }
    }

    // Getters and Setters
    public String getInstrumentKey() {
        return instrumentKey;
    }

    public void setInstrumentKey(String instrumentKey) {
        this.instrumentKey = instrumentKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public double getLtp() {
        return ltp;
    }

    public void setLtp(double ltp) {
        this.ltp = ltp;
    }

    public long getLtq() {
        return ltq;
    }

    public void setLtq(long ltq) {
        this.ltq = ltq;
    }

    public List<BookEntry> getBids() {
        return bids;
    }

    public void setBids(List<BookEntry> bids) {
        this.bids = bids;
    }

    public List<BookEntry> getAsks() {
        return asks;
    }

    public void setAsks(List<BookEntry> asks) {
        this.asks = asks;
    }

    public double getBestBid() {
        return bestBid;
    }

    public void setBestBid(double bestBid) {
        this.bestBid = bestBid;
    }

    public double getBestAsk() {
        return bestAsk;
    }

    public void setBestAsk(double bestAsk) {
        this.bestAsk = bestAsk;
    }
}
