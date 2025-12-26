package com.trading.hf.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.upstox.marketdatafeederv3udapi.rpc.proto.MarketDataFeed;

public class ProtobufDecoder {

    public MarketDataFeed.FeedResponse decode(byte[] data) throws InvalidProtocolBufferException {
        return MarketDataFeed.FeedResponse.parseFrom(data);
    }
}
