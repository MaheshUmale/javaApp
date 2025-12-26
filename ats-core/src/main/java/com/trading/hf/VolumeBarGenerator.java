package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VolumeBarGenerator implements EventHandler<MarketEvent> {

    private final long volumeThreshold;
    private final ConcurrentHashMap<String, VolumeBar> runningBars = new ConcurrentHashMap<>();
    private final Consumer<VolumeBar> barConsumer;
    private Consumer<VolumeBar> dashboardConsumer;
    private volatile VolumeBar lastCompletedBar;

    public VolumeBarGenerator(long volumeThreshold, Consumer<VolumeBar> barConsumer) {
        this.volumeThreshold = volumeThreshold;
        this.barConsumer = barConsumer;
    }

    public void setDashboardConsumer(Consumer<VolumeBar> dashboardConsumer) {
        this.dashboardConsumer = dashboardConsumer;
    }

    @Override
    public void onEvent(MarketEvent event, long sequence, boolean endOfBatch) {
        runningBars.compute(event.getSymbol(), (symbol, bar) -> {
            if (bar == null) {
                bar = new VolumeBar(symbol, event.getLtt(), event.getLtp(), event.getLtq());
            } else {
                int side = determineSide(event);
                bar.addTick(event.getLtp(), event.getLtq(), side);
            }

            if (bar.getVolume() >= volumeThreshold) {
                bar.setOrderBookImbalance(calculateOBI(event));
                lastCompletedBar = bar;
                barConsumer.accept(bar); // for console logging
                if (dashboardConsumer != null) {
                    dashboardConsumer.accept(bar); // for dashboard broadcasting
                }
                return null; // Start a new bar
            }
            return bar;
        });
    }

    public VolumeBar getLastBar() {
        return lastCompletedBar;
    }

    private int determineSide(MarketEvent event) {
        if (event.getBestAskPrice() > 0 && event.getLtp() >= event.getBestAskPrice()) {
            return 1; // Aggressive Buyer
        } else if (event.getBestBidPrice() > 0 && event.getLtp() <= event.getBestBidPrice()) {
            return -1; // Aggressive Seller
        }
        return 0; // Neutral or indeterminate
    }

    private double calculateOBI(MarketEvent event) {
        if (event.getTbq() + event.getTsq() == 0) {
            return 0;
        }
        return (event.getTbq() - event.getTsq()) / (event.getTbq() + event.getTsq());
    }
}
