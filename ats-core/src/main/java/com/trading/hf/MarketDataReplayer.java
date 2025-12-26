package com.trading.hf;

import com.lmax.disruptor.RingBuffer;

import java.sql.*;
import java.time.Instant;

public class MarketDataReplayer {

    private final RingBuffer<MarketEvent> ringBuffer;
    private final String questdbConnectionString;

    public MarketDataReplayer(RingBuffer<MarketEvent> ringBuffer, String questdbConnectionString) {
        this.ringBuffer = ringBuffer;
        this.questdbConnectionString = questdbConnectionString;
    }

    public void replay(String symbol, Instant startTime, Instant endTime) {
        String query = "SELECT * FROM ticks WHERE symbol = ? AND timestamp BETWEEN ? AND ? ORDER BY timestamp ASC";

        try (Connection conn = DriverManager.getConnection(questdbConnectionString);
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, symbol);
            stmt.setTimestamp(2, Timestamp.from(startTime));
            stmt.setTimestamp(3, Timestamp.from(endTime));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sequence = ringBuffer.next();
                    try {
                        MarketEvent event = ringBuffer.get(sequence);
                        event.setSymbol(rs.getString("symbol"));
                        event.setLtp(rs.getDouble("ltp"));
                        event.setLtq(rs.getLong("ltq"));
                        event.setTs(rs.getTimestamp("timestamp").getTime());
                        // ... set other fields from the result set
                    } finally {
                        ringBuffer.publish(sequence);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
