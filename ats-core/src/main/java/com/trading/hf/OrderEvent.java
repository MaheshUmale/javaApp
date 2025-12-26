package com.trading.hf;

import com.lmax.disruptor.EventFactory;

public class OrderEvent {
    private String orderId;
    private String instrumentKey;
    private String side;
    private String orderType;
    private int quantity;
    private double price;
    private double triggerPrice;
    private String status;
    private double fillPrice;
    private int fillQuantity;
    private long timestamp;

    public void set(String orderId, String instrumentKey, String side, String orderType, int quantity, 
                    double price, double triggerPrice, String status, double fillPrice, int fillQuantity, long timestamp) {
        this.orderId = orderId;
        this.instrumentKey = instrumentKey;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.triggerPrice = triggerPrice;
        this.status = status;
        this.fillPrice = fillPrice;
        this.fillQuantity = fillQuantity;
        this.timestamp = timestamp;
    }

    public String getOrderId() { return orderId; }
    public String getInstrumentKey() { return instrumentKey; }
    public String getSide() { return side; }
    public String getOrderType() { return orderType; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public double getTriggerPrice() { return triggerPrice; }
    public String getStatus() { return status; }
    public double getFillPrice() { return fillPrice; }
    public int getFillQuantity() { return fillQuantity; }
    public long getTimestamp() { return timestamp; }

    public static final EventFactory<OrderEvent> EVENT_FACTORY = OrderEvent::new;
}
