package com.trading.hf.service;

import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed;

public interface MarketDataFeedListener {
    void onMarketDataFeed(MarketDataFeed.Feed feed);
}
