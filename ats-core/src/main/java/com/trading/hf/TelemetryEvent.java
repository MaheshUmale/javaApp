package com.trading.hf;

import com.lmax.disruptor.EventFactory;

public class TelemetryEvent {
    private String processorName;
    private long remainingCapacity;
    private long processingLagMs;
    private long networkLagMs;
    private long timestamp;

    public void set(String processorName, long remainingCapacity, long processingLagMs, long networkLagMs, long timestamp) {
        this.processorName = processorName;
        this.remainingCapacity = remainingCapacity;
        this.processingLagMs = processingLagMs;
        this.networkLagMs = networkLagMs;
        this.timestamp = timestamp;
    }

    public String getProcessorName() { return processorName; }
    public long getRemainingCapacity() { return remainingCapacity; }
    public long getProcessingLagMs() { return processingLagMs; }
    public long getNetworkLagMs() { return networkLagMs; }
    public long getTimestamp() { return timestamp; }

    public static final EventFactory<TelemetryEvent> EVENT_FACTORY = TelemetryEvent::new;
}
