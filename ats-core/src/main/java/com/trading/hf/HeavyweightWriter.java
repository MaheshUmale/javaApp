package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class HeavyweightWriter implements EventHandler<HeavyweightEvent>, AutoCloseable {
    private final Sender sender;

    public HeavyweightWriter() {
        this.sender = Sender.builder(Sender.Transport.TCP)
                .address("localhost:9009")
                .build();
    }

    @Override
    public void onEvent(HeavyweightEvent event, long sequence, boolean endOfBatch) {
        sender.table("heavyweight_logs")
                .symbol("symbol", event.getSymbol())
                .doubleColumn("price", event.getPrice())
                .doubleColumn("weight", event.getWeight())
                .doubleColumn("delta", event.getDelta())
                .doubleColumn("aggregate_delta", event.getAggregateDelta())
                .at(Instant.ofEpochMilli(event.getTimestamp()));
        
        if (endOfBatch) {
            sender.flush();
        }
    }

    @Override
    public void close() {
        sender.close();
    }
}
