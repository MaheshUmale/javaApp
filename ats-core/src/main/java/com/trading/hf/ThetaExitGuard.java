package com.trading.hf;

import com.lmax.disruptor.EventHandler;

public class ThetaExitGuard implements EventHandler<MarketEvent> {

    private final PositionManager positionManager;
    private final UpstoxOrderManager orderManager;
    private static final double THETA_DECAY_THRESHOLD = 0.5; // Example threshold

    public ThetaExitGuard(PositionManager positionManager, UpstoxOrderManager orderManager) {
        this.positionManager = positionManager;
        this.orderManager = orderManager;
    }

    @Override
    public void onEvent(MarketEvent event, long sequence, boolean endOfBatch) {
        positionManager.getAllPositions().forEach((instrumentKey, position) -> {
            if (instrumentKey.equals(event.getSymbol())) {
                double pnl = 0;
                if (position.getSide().equals("BUY")) {
                    pnl = (event.getLtp() - position.getEntryPrice()) * position.getQuantity();
                } else {
                    pnl = (position.getEntryPrice() - event.getLtp()) * position.getQuantity();
                }

                long timeInMarketSeconds = (event.getTs() - position.getEntryTimestamp()) / 1000;
                double dayFraction = timeInMarketSeconds / 86400.0;
                double thetaDecay = event.getTheta() * dayFraction;

                if (pnl + thetaDecay < -THETA_DECAY_THRESHOLD) {
                    orderManager.placeOrder(
                            instrumentKey,
                            position.getQuantity(),
                            position.getSide().equals("BUY") ? "SELL" : "BUY",
                            "MARKET",
                            0
                    );
                    positionManager.removePosition(instrumentKey);
                }
            }
        });
    }
}
