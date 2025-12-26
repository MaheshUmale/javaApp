package com.trading.hf;

import com.google.gson.Gson;
import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class RawFeedWriter implements EventHandler<RawFeedEvent>, AutoCloseable {

    private final Sender sender;
    private final Gson gson = new Gson();
    private long lastFlushTime = System.currentTimeMillis();
    private long eventCount = 0;
    private final boolean persistenceEnabled;

    public RawFeedWriter() {
        this.persistenceEnabled = ConfigLoader.getBooleanProperty("questdb.enabled", false);
        if (persistenceEnabled) {
            this.sender = Sender.builder(Sender.Transport.TCP)
                    .address("localhost:9009")
                    .build();
        } else {
            this.sender = null;
        }
    }

    @Override
    public void onEvent(RawFeedEvent event, long sequence, boolean endOfBatch) {
        if (!persistenceEnabled || sender == null) {
            return;
        }

        boolean persistDepth = ConfigLoader.getBooleanProperty("database.persistence.depth", false);
        
        sender.table("raw_market_feed")
                .symbol("instrumentKey", event.getInstrumentKey())
                .doubleColumn("ltp", event.getLtp())
                .longColumn("ltq", event.getLtq())
                .doubleColumn("best_bid", event.getBestBid())
                .doubleColumn("best_ask", event.getBestAsk());
        
        if (persistDepth) {
            String bidsJson = gson.toJson(event.getBids());
            String asksJson = gson.toJson(event.getAsks());
            sender.stringColumn("bids", bidsJson)
                  .stringColumn("asks", asksJson);
        }

        sender.at(Instant.ofEpochMilli(event.getTimestamp()));

        eventCount++;

        long currentTime = System.currentTimeMillis();
        if (eventCount >= 1000 || currentTime - lastFlushTime >= 1000) {
            sender.flush();
            lastFlushTime = currentTime;
            eventCount = 0;
        }
    }

    @Override
    public void close() {
        if (sender != null) {
            sender.close();
        }
    }
}
