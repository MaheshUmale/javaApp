package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class QuestDBWriter implements EventHandler<MarketEvent>, AutoCloseable {

    private final Sender sender;
    private long lastFlushTime = System.currentTimeMillis();
    private long eventCount = 0;

    public QuestDBWriter() {
        this.sender = Sender.builder(Sender.Transport.TCP)
                .address("localhost:9009")
                .build();
    }

    @Override
    public void onEvent(MarketEvent event, long sequence, boolean endOfBatch) {
        sender.table("ticks")
                .symbol("symbol", event.getSymbol())
                .doubleColumn("ltp", event.getLtp())
                .longColumn("ltq", event.getLtq())
                .longColumn("ltt", event.getLtt())
                .doubleColumn("cp", event.getCp())
                .doubleColumn("tbq", event.getTbq())
                .doubleColumn("tsq", event.getTsq())
                .longColumn("vtt", event.getVtt())
                .doubleColumn("oi", event.getOi())
                .doubleColumn("iv", event.getIv())
                .doubleColumn("atp", event.getAtp())
                .doubleColumn("best_bid", event.getBestBidPrice())
                .doubleColumn("best_ask", event.getBestAskPrice())
                .at(Instant.ofEpochMilli(event.getTs()));

        eventCount++;

        long currentTime = System.currentTimeMillis();
        if (eventCount >= 10000 || currentTime - lastFlushTime >= 1000) {
            sender.flush();
            lastFlushTime = currentTime;
            eventCount = 0;
        }
    }

    @Override
    public void close() {
        sender.close();
    }
}
