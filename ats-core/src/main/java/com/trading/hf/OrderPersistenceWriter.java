package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import io.questdb.client.Sender;
import java.time.Instant;

public class OrderPersistenceWriter implements EventHandler<OrderEvent>, AutoCloseable {
    private final Sender sender;

    public OrderPersistenceWriter() {
        this.sender = Sender.builder(Sender.Transport.TCP)
                .address("localhost:9009")
                .build();
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        sender.table("orders")
                .symbol("order_id", event.getOrderId())
                .symbol("instrument_key", event.getInstrumentKey())
                .symbol("side", event.getSide())
                .symbol("order_type", event.getOrderType())
                .symbol("status", event.getStatus())
                .longColumn("quantity", event.getQuantity())
                .doubleColumn("price", event.getPrice())
                .doubleColumn("trigger_price", event.getTriggerPrice())
                .doubleColumn("fill_price", event.getFillPrice())
                .longColumn("fill_quantity", event.getFillQuantity())
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
