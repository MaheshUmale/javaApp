package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class SignalPersistenceWriter implements EventHandler<SignalEvent>, AutoCloseable {
    private final Sender sender;

    public SignalPersistenceWriter() {
        this.sender = Sender.builder(Sender.Transport.TCP)
                .address("localhost:9009")
                .build();
    }

    @Override
    public void onEvent(SignalEvent event, long sequence, boolean endOfBatch) {
        sender.table("signal_logs")
                .symbol("symbol", event.getSymbol())
                .symbol("signal_type", event.getType())
                .doubleColumn("price", event.getPrice())
                .doubleColumn("vah", event.getVah())
                .doubleColumn("val", event.getVal())
                .doubleColumn("poc", event.getPoc())
                .doubleColumn("delta", event.getDelta())
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
