package com.trading.hf.dashboard;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import io.javalin.websocket.WsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DashboardService {
    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);
    private Javalin app;
    private final ConcurrentLinkedQueue<WsContext> sessions = new ConcurrentLinkedQueue<>();
    private final AtomicReference<String> lastMessage = new AtomicReference<>();
    private static OrderController orderController;
    private static com.trading.hf.VirtualPositionManager virtualPositionManager;

    public static void setPaperTrading(com.trading.hf.VirtualPositionManager vpm) {
        virtualPositionManager = vpm;
        orderController = new OrderController(vpm);
    }

    public void start() {
        app = Javalin.create(config -> {
            config.staticFiles.add("/public");
        });

        app.ws("/data", ws -> {
            ws.onConnect(ctx -> {
                ctx.enableAutomaticPings();
                sessions.add(ctx);
                logger.info("Browser Connected: {}", ctx.sessionId());
                
                // Initial send with a safety delay
                CompletableFuture.delayedExecutor(300, TimeUnit.MILLISECONDS).execute(() -> {
                    String msg = lastMessage.get();
                    if (msg != null && ctx.session.isOpen()) {
                        ctx.send(msg);
                    }
                });
            });

            ws.onClose(ctx -> {
                sessions.remove(ctx);
                logger.info("Browser Disconnected (Code: {})", ctx.status());
            });

            ws.onError(ctx -> {
                // Suppress ClosedChannelException to keep logs clean
                if (!(ctx.error() instanceof java.io.IOException)) {
                    logger.error("WS Error: {}", ctx.error().getMessage());
                }
            });
        });

        // Manual Order API
        app.post("/api/order/place", ctx -> {
            if (orderController != null) {
                orderController.placeOrder(ctx);
            } else {
                ctx.status(503).json(java.util.Map.of("error", "Paper trading not initialized"));
            }
        });

        // Performance API
        app.get("/api/performance", ctx -> {
            if (virtualPositionManager != null) {
                com.trading.hf.PerformanceTracker tracker = virtualPositionManager.getPerformanceTracker();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                String json = gson.toJson(java.util.Map.of(
                    "totalPnL", tracker.getTotalPnL(),
                    "totalTrades", tracker.getTotalTrades(),
                    "winners", tracker.getWinners(),
                    "losers", tracker.getLosers(),
                    "winRate", tracker.getWinRate(),
                    "avgPnL", tracker.getAveragePnL(),
                    "maxWin", tracker.getMaxWin(),
                    "maxLoss", tracker.getMaxLoss()
                ));
                ctx.result(json).contentType("application/json");
            } else {
                ctx.status(503).result("{\"error\": \"Performance tracking not enabled\"}").contentType("application/json");
            }
        });

        app.start(7070);
        logger.info("Dashboard WSS Bridge listening on ws://localhost:7070/data");
    }

    public void broadcast(String message) {
        lastMessage.set(message);
        sessions.removeIf(s -> !s.session.isOpen());
        for (WsContext session : sessions) {
            try {
                if (session.session.isOpen()) {
                    session.send(message);
                }
            } catch (Exception e) {
                // Channel already dead
            }
        }
    }

    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }
}