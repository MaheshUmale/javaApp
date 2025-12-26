package com.trading.hf;

import com.lmax.disruptor.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public class QuestDBReplayer implements IDataReplayer {
    private static final Logger logger = LoggerFactory.getLogger(QuestDBReplayer.class);
    private final RingBuffer<MarketEvent> ringBuffer;
    private final long delayMs;

    public QuestDBReplayer(RingBuffer<MarketEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
        this.delayMs = Long.parseLong(ConfigLoader.getProperty("simulation.event.delay.ms", "10"));
    }

    @Override
    public void start() {
        logger.info("Starting QuestDB Replayer...");
        
        // QuestDB uses PG wire protocol on port 8812
        String url = "jdbc:postgresql://localhost:8812/qdb";
        Properties props = new Properties();
        props.setProperty("user", "admin");
        props.setProperty("password", "quest");
        props.setProperty("ssl", "false");

        try (Connection conn = DriverManager.getConnection(url, props);
             Statement stmt = conn.createStatement()) {
            
            // Query ticks table ordered by timestamp (ltt)
            String query = "SELECT * FROM ticks ORDER BY ltt";
            logger.info("Executing query: {}", query);
            
            try (ResultSet rs = stmt.executeQuery(query)) {
                int count = 0;
                while (rs.next()) {
                    String symbol = rs.getString("symbol");
                    double ltp = rs.getDouble("ltp");
                    long ltq = rs.getLong("ltq");
                    long ltt = rs.getLong("ltt");
                    double cp = rs.getDouble("cp");
                    double tbq = rs.getDouble("tbq");
                    double tsq = rs.getDouble("tsq");
                    long vtt = rs.getLong("vtt");
                    double oi = rs.getDouble("oi");
                    double iv = rs.getDouble("iv");
                    double atp = rs.getDouble("atp");
                    double bestBid = rs.getDouble("best_bid");
                    double bestAsk = rs.getDouble("best_ask");

                    publishMarketUpdate(symbol, ltp, ltq, ltt, cp, tbq, tsq, vtt, oi, iv, atp, bestBid, bestAsk);
                    
                    count++;
                    if (count % 5000 == 0) {
                        logger.info("Replayed {} ticks...", count);
                    }

                    if (delayMs > 0) {
                        Thread.sleep(delayMs);
                    }
                }
                logger.info("Replay finished. Total ticks: {}", count);
            }
        } catch (Exception e) {
            logger.error("Error during QuestDB replay", e);
            System.err.println("QuestDB Replay Error: " + e.getMessage());
        }
    }

    private void publishMarketUpdate(String symbol, double ltp, long ltq, long ltt, double cp, 
                                   double tbq, double tsq, long vtt, double oi, double iv, double atp,
                                   double bestBid, double bestAsk) {
        long sequence = ringBuffer.next();
        try {
            MarketEvent event = ringBuffer.get(sequence);
            event.setSymbol(symbol);
            event.setLtp(ltp);
            event.setLtt(ltt);
            event.setLtq(ltq);
            event.setCp(cp);
            event.setTbq(tbq);
            event.setTsq(tsq);
            event.setVtt(vtt);
            event.setOi(oi);
            event.setIv(iv);
            event.setAtp(atp);
            event.setTs(ltt);
            // In replay, we might not have stored best bid/ask in the same granular tick table
            // Defaulting to LTP if missing, but typically 'ticks' table is for time-series analysis.
            event.setBestBidPrice(bestBid > 0 ? bestBid : ltp);
            event.setBestAskPrice(bestAsk > 0 ? bestAsk : ltp);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
