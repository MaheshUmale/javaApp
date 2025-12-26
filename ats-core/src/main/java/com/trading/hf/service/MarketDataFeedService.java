package com.trading.hf.service;

import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed;

public class MarketDataFeedService {

    private final MarketDataFeedListener listener;

    public MarketDataFeedService(MarketDataFeedListener listener) {
        this.listener = listener;
    }

    public void processMarketDataFeed(MarketDataFeed.Feed feed) {
        if (listener != null) {
            listener.onMarketDataFeed(feed);
        }
    }
}
