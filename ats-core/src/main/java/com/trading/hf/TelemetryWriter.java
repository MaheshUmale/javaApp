package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class TelemetryWriter implements EventHandler<TelemetryEvent>, AutoCloseable {
    private final Sender sender;

    public TelemetryWriter() {
        this.sender = Sender.builder(Sender.Transport.TCP)
                .address("localhost:9009")
                .build();
    }

    @Override
    public void onEvent(TelemetryEvent event, long sequence, boolean endOfBatch) {
        sender.table("telemetry")
                .symbol("processor", event.getProcessorName())
                .longColumn("remaining_capacity", event.getRemainingCapacity())
                .longColumn("proc_lag_ms", event.getProcessingLagMs())
                .longColumn("net_lag_ms", event.getNetworkLagMs())
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
