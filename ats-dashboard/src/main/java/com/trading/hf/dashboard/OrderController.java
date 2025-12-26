package com.trading.hf.dashboard;

import com.trading.hf.VirtualPositionManager;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final VirtualPositionManager virtualPositionManager;

    public OrderController(VirtualPositionManager virtualPositionManager) {
        this.virtualPositionManager = virtualPositionManager;
    }

    public void placeOrder(Context ctx) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            OrderRequest request = gson.fromJson(ctx.body(), OrderRequest.class);
            
            if (request.instrumentKey == null || request.quantity <= 0) {
                ctx.status(400).result("{\"error\": \"Invalid request - Missing instrument or quantity\"}").contentType("application/json");
                return;
            }

            // Route to Virtual Position Manager (Paper Trading)
            virtualPositionManager.openPosition(
                request.instrumentKey,
                request.quantity,
                request.side, // BUY or SELL
                request.price,
                System.currentTimeMillis(),
                0.0, 0.0, 0.0 // Manual trade doesn't have VAH/VAL/POC context by default
            );

            logger.info("[MANUAL-PAPER] Placed {} order for {} x{} @ {}", 
                request.side, request.instrumentKey, request.quantity, request.price);

            ctx.result("{\"success\": true, \"message\": \"Manual Paper Trade placed successfully\"}").contentType("application/json");

        } catch (Exception e) {
            logger.error("Error placing manual paper order", e);
            ctx.status(500).result("{\"error\": \"" + e.getMessage() + "\"}").contentType("application/json");
        }
    }

    public static class OrderRequest {
        public String instrumentKey;
        public int quantity;
        public String side;
        public String orderType;
        public double price;
    }
}
