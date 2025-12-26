package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class AutoInstrumentManager {
    private static final Logger logger = LoggerFactory.getLogger(AutoInstrumentManager.class);
    private final InstrumentLoader loader;
    private final String mappingFile;
    private Map<String, Object> mappedKeys = new HashMap<>();

    public AutoInstrumentManager(InstrumentLoader loader, String mappingFile) {
        this.loader = loader;
        this.mappingFile = mappingFile;
    }

    public void initialize() {
        if (shouldUpdateDaily()) {
            logger.info("Starting daily instrument update...");
            loader.processDaily();
            performExtraction();
            saveMapping();
        } else {
            logger.info("Loading existing instrument mapping...");
            loadMapping();
        }
    }

    private boolean shouldUpdateDaily() {
        // Simple check: if mapping file doesn't exist or is from a previous day
        if (!Files.exists(Paths.get(mappingFile))) return true;
        try {
            long lastModified = Files.getLastModifiedTime(Paths.get(mappingFile)).toMillis();
            Calendar last = Calendar.getInstance();
            last.setTimeInMillis(lastModified);
            Calendar now = Calendar.getInstance();
            return last.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ||
                   last.get(Calendar.YEAR) != now.get(Calendar.YEAR);
        } catch (IOException e) {
            return true;
        }
    }

    private void performExtraction() {
        List<String> top10Symbols = Arrays.asList(
            "RELIANCE", "HDFCBANK", "BHARTIARTL", "TCS", "ICICIBANK",
            "SBIN", "INFY", "BAJFINANCE", "LT", "HINDUNILVR"
        );

        Map<String, String> equities = loader.getKeysForSymbols(top10Symbols);
        mappedKeys.put("equities", equities);
        
        String niftyIndexKey = loader.getNiftyIndexKey();
        mappedKeys.put("nifty_index", niftyIndexKey);
        mappedKeys.put("nifty_future", loader.getNiftyFutureKey());

        // Extract FO strikes for NIFTY
        // We'll use the last_price of NIFTY 50 to determine initial ATM
        double niftyPrice = 24000; // Default fallback
        String url = "jdbc:sqlite:instruments.db"; // Placeholder, should be configurable
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + loader.getDbPath())) {
            String sql = "SELECT last_price FROM instruments WHERE instrument_key = 'NSE_INDEX|Nifty 50'";
            try (java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) niftyPrice = rs.getDouble("last_price");
            }
        } catch (Exception e) {
            logger.error("Error getting Nifty price", e);
        }

        List<String> optionKeys = loader.getOptionKeysByATM("NIFTY", niftyPrice, 5);
        mappedKeys.put("nifty_options", optionKeys);
        logger.info("Extracted {} NIFTY options for ATM {}", optionKeys.size(), niftyPrice);
    }

    private void saveMapping() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.writeString(Paths.get(mappingFile), gson.toJson(mappedKeys));
            logger.info("Instrument mapping saved to {}", mappingFile);
        } catch (IOException e) {
            logger.error("Failed to save mapping", e);
        }
    }

    private void loadMapping() {
        Gson gson = new Gson();
        try {
            String content = Files.readString(Paths.get(mappingFile));
            mappedKeys = gson.fromJson(content, Map.class);
        } catch (IOException e) {
            logger.error("Failed to load mapping", e);
        }
    }

    public String getEquityKey(String symbol) {
        Map<String, String> equities = (Map<String, String>) mappedKeys.get("equities");
        return equities != null ? equities.get(symbol) : null;
    }

    public String getNiftyIndexKey() {
        return (String) mappedKeys.get("nifty_index");
    }

    public String getNiftyFutureKey() {
        return (String) mappedKeys.get("nifty_future");
    }

    public Map<String, Object> getMappedKeys() {
        return mappedKeys;
    }
}
