package com.trading.hf.dashboard;

import java.util.List;

public class DashboardViewModel {
    // Header (Dedicated to NIFTY Index)
    public long timestamp;
    public double indexSpot;
    public double indexFuture;
    public double indexBasis;
    public double pcr;
    public double indexChange;

    // Focus Instruments (Segregated to prevent flickering)
    public String stockKey;
    public String stockSymbol;
    public double stockLtp;
    public String optionKey;
    public String optionSymbol;
    public double optionLtp;

    // System Health
    public long wssLatency;
    public long questDbLag; // Renamed from questDbWriteLag
    public double disruptor; // Disruptor fill rate (%)

    // Auction Profile
    public MarketProfileViewModel auctionProfile;
    public String totalVol;

    // Heavyweights
    public List<HeavyweightViewModel> heavyweights;
    public double aggregateWeightedDelta;

    // Option Chain
    public List<OptionViewModel> option_window;

    // Active Trades
    public List<TradeViewModel> activeTrades;

    // Sentiment & Alerts
    public String auctionState;
    public List<AlertViewModel> alerts;

    // Trade Panel
    public double theta_gcr;
    public OhlcViewModel ohlc;
    public VolumeBarViewModel volumeBar;

    public static class VolumeBarViewModel {
        public double orderBookImbalance;
        public long startTime;
        public double open;
        public double high;
        public double low;
        public double close;
    }

    // Inner classes for nested structures
    public static class MarketProfileViewModel {
        public double vah;
        public double val;
        public double poc;
    }

    public static class HeavyweightViewModel {
        public int rank;
        public String name;
        public String companyName;
        public String priceChange; // Price / % Change string
        public long qtp; // Quantity Traded Percentage/Value
        public double delta;
        public String weight;
        public String sector;
    }

    public static class OptionViewModel {
        public int strike;
        public String type; // "CE" or "PE"
        public double ltp;
        public long oi;
        public double oi_chg;
        public String sentiment;
    }

    public static class TradeViewModel {
        public String symbol;
        public double entry;
        public double ltp;
        public int qty;
        public double pnl;
        public String reason;
    }

    public static class AlertViewModel {
        public String type; // success, warning, error
        public String message;
    }

    public static class OhlcViewModel {
        public double open;
        public double high;
        public double low;
        public double close;
    }
}
