package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class InstrumentLoader {
    private static final Logger logger = LoggerFactory.getLogger(InstrumentLoader.class);
    private final String dbPath;
    private final String jsonGzPath;
    private final String jsonPath;

    public InstrumentLoader(String dbPath, String jsonGzPath, String jsonPath) {
        this.dbPath = dbPath;
        this.jsonGzPath = jsonGzPath;
        this.jsonPath = jsonPath;
    }

    public void processDaily() {
        try {
            if (Files.exists(Paths.get(jsonGzPath))) {
                logger.info("Extracting {}...", jsonGzPath);
                extractGz(jsonGzPath, jsonPath);
            }

            if (Files.exists(Paths.get(jsonPath))) {
                logger.info("Loading {} into SQLite...", jsonPath);
                loadIntoSQLite();
            } else {
                logger.warn("NSE.json not found at {}", jsonPath);
            }
        } catch (Exception e) {
            logger.error("Error processing daily instruments", e);
        }
    }

    private void extractGz(String gzFile, String outFile) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(gzFile));
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void loadIntoSQLite() throws SQLException, IOException {
        String url = "jdbc:sqlite:" + dbPath;
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("DROP TABLE IF EXISTS instruments");
            stmt.execute("CREATE TABLE instruments (" +
                    "instrument_key TEXT PRIMARY KEY," +
                    "exchange_token TEXT," +
                    "trading_symbol TEXT," +
                    "name TEXT," +
                    "last_price REAL," +
                    "expiry TEXT," +
                    "strike REAL," +
                    "tick_size REAL," +
                    "lot_size INTEGER," +
                    "instrument_type TEXT," +
                    "segment TEXT," +
                    "exchange TEXT," +
                    "underlying_symbol TEXT," +
                    "underlying_key TEXT" +
                    ")");

            conn.setAutoCommit(false);
            String sql = "INSERT INTO instruments VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                Gson gson = new Gson();
                try (JsonReader reader = new JsonReader(new FileReader(jsonPath))) {
                    reader.beginArray();
                    int count = 0;
                    while (reader.hasNext()) {
                        Map<String, Object> map = gson.fromJson(reader, Map.class);
                        pstmt.setString(1, getString(map.get("instrument_key")));
                        pstmt.setString(2, getString(map.get("exchange_token")));
                        pstmt.setString(3, getString(map.get("trading_symbol")));
                        pstmt.setString(4, getString(map.get("name")));
                        pstmt.setDouble(5, getDouble(map.get("last_price")));
                        pstmt.setString(6, getString(map.get("expiry")));
                        pstmt.setDouble(7, getDouble(map.get("strike")));
                        pstmt.setDouble(8, getDouble(map.get("tick_size")));
                        pstmt.setInt(9, getInt(map.get("lot_size")));
                        pstmt.setString(10, getString(map.get("instrument_type")));
                        pstmt.setString(11, getString(map.get("segment")));
                        pstmt.setString(12, getString(map.get("exchange")));
                        pstmt.setString(13, getString(map.get("underlying_symbol")));
                        pstmt.setString(14, getString(map.get("underlying_key")));
                        pstmt.addBatch();

                        if (++count % 5000 == 0) {
                            pstmt.executeBatch();
                            conn.commit();
                        }
                    }
                    pstmt.executeBatch();
                    conn.commit();
                    logger.info("Loaded {} instruments into SQLite.", count);
                }
            }
        }
    }

    private String getString(Object o) {
        if (o == null) return null;
        if (o instanceof String) return (String) o;
        return String.valueOf(o);
    }

    private double getDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        return 0.0;
    }

    private int getInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        return 0;
    }

    public Map<String, String> getKeysForSymbols(List<String> symbols) {
        Map<String, String> map = new HashMap<>();
        String url = "jdbc:sqlite:" + dbPath;
        String sql = "SELECT trading_symbol, instrument_key FROM instruments WHERE trading_symbol = ? AND segment = 'NSE_EQ' AND instrument_type = 'EQ'";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (String symbol : symbols) {
                pstmt.setString(1, symbol);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        map.put(rs.getString("trading_symbol"), rs.getString("instrument_key"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying SQLite", e);
        }
        return map;
    }

    public List<String> getOptionKeysByATM(String underlying, double atmPrice, int range) {
        String expiry = getNearestExpiry(underlying);
        if (expiry == null) return Collections.emptyList();
        
        int atmStrike = (int) (Math.round(atmPrice / 50.0) * 50);
        return getOptionKeys(underlying, atmStrike, range, expiry);
    }

    public String getNiftyIndexKey() {
        return getKey("NSE_INDEX|Nifty 50");
    }

    public String getNiftyFutureKey() {
        // Simple logic for current month future - usually has 'FUT' and nearest expiry
        String url = "jdbc:sqlite:" + dbPath;
        String sql = "SELECT instrument_key FROM instruments WHERE segment = 'NSE_FO' AND instrument_type = 'FUT' AND trading_symbol LIKE 'NIFTY%' ORDER BY expiry ASC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getString("instrument_key");
        } catch (SQLException e) {
            logger.error("Error querying SQLite for Nifty Future", e);
        }
        return null;
    }

    public List<String> getOptionKeys(String index, double atmStrike, int range, String expiry) {
        List<String> keys = new ArrayList<>();
        String url = "jdbc:sqlite:" + dbPath;
        // Find nearest 5 strikes up and down
        // Upstox trading_symbol format: "NIFTY 26100 PE 30 DEC 25"
        // We'll query by underlying and strike range
        String sql = "SELECT instrument_key FROM instruments " +
                     "WHERE segment = 'NSE_FO' AND underlying_symbol = ? " +
                     "AND strike >= ? AND strike <= ? " +
                     "AND expiry = ? " +
                     "AND instrument_type IN ('CE', 'PE')";
        
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, index);
            pstmt.setDouble(2, atmStrike - (range * 50)); // Assuming 50 point intervals for NIFTY
            pstmt.setDouble(3, atmStrike + (range * 50));
            pstmt.setString(4, expiry);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString("instrument_key"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying options", e);
        }
        return keys;
    }

    public String getNearestExpiry(String index) {
        String url = "jdbc:sqlite:" + dbPath;
        String sql = "SELECT expiry FROM instruments WHERE segment = 'NSE_FO' AND underlying_symbol = ? AND expiry >= date('now') ORDER BY expiry ASC LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, index);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("expiry");
            }
        } catch (SQLException e) {
            logger.error("Error finding nearest expiry", e);
        }
        return null;
    }

    private String getKey(String instrumentKey) {
        String url = "jdbc:sqlite:" + dbPath;
        String sql = "SELECT instrument_key FROM instruments WHERE instrument_key = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, instrumentKey);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getString("instrument_key");
            }
        } catch (SQLException e) {
            logger.error("Error querying SQLite", e);
        }
        return null;
    }

    public String getDbPath() {
        return dbPath;
    }
}
