package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lmax.disruptor.RingBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class MySampleDataReplayer implements IDataReplayer {
    private static final Logger logger = LoggerFactory.getLogger(MySampleDataReplayer.class);
    private final RingBuffer<MarketEvent> ringBuffer;
    private final String dataDirectory;
    private final Gson gson = new Gson();
    private final long simulationEventDelayMs;

    // Use a single, generated data file for predictable backtesting
    private final List<String> dataFiles = Arrays.asList(
            "simulation_data.json.gz");

    public MySampleDataReplayer(RingBuffer<MarketEvent> ringBuffer, String dataDirectory) {
        this.ringBuffer = ringBuffer;
        this.dataDirectory = dataDirectory;
        this.simulationEventDelayMs = Long.parseLong(ConfigLoader.getProperty("simulation.event.delay.ms", "10"));
    }

    public void start() {
        logger.info("Starting data replay from classpath directory: {}", dataDirectory);
        for (int i = 0; i < 10; i++) {
            for (String fileName : dataFiles) {
                processFile(dataDirectory + "/" + fileName);
            }
        }
        logger.info("Data replay finished.");
    }

    private void processFile(String filePath) {
        logger.info("Processing file: {}", filePath);
        InputStream resourceIs = getClass().getClassLoader().getResourceAsStream(filePath);
        
        try (InputStream is = (resourceIs != null) ? resourceIs : new java.io.FileInputStream(filePath);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new GZIPInputStream(Objects.requireNonNull(is))))) {

            String jsonData = reader.lines().collect(Collectors.joining());
            Type type = new TypeToken<List<Map<String, Object>>>() {
            }.getType();
            List<Map<String, Object>> dataList = gson.fromJson(jsonData, type);

            for (Map<String, Object> data : dataList) {
                // Transform the record to the new structure
                Map<String, Object> restructuredData = transformRecord(data);
                publishMarketUpdate(restructuredData);
                try {
                    Thread.sleep(simulationEventDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Data replay interrupted");
                    return;
                }
            }

        } catch (Exception e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void publishMarketUpdate(Map<String, Object> data) {
        try {
            Map<String, Object> feeds = (Map<String, Object>) data.get("feeds");
            if (feeds == null || feeds.isEmpty()) {
                return;
            }

            String instrumentKey = feeds.keySet().iterator().next();
            Map<String, Object> feedData = (Map<String, Object>) feeds.get(instrumentKey);
            Map<String, Object> ff = (Map<String, Object>) feedData.get("fullFeed");

            Map<String, Object> marketData = (Map<String, Object>) ff.get("marketFF");
            if (marketData == null) {
                marketData = (Map<String, Object>) ff.get("indexFF");
            }
            if (marketData == null)
                return;

            Map<String, Object> ltpc = (Map<String, Object>) marketData.get("ltpc");
            if (ltpc == null)
                return;

            double ltp = getDouble(ltpc.get("ltp"));
            long ltq = getLong(ltpc.get("ltq"));
            long ltt = getLong(ltpc.get("ltt"));
            double cp = getDouble(ltpc.get("cp"));

            double tbq = 0, tsq = 0, atp = 0, oi = 0;
            long vtt = 0;
            double bestBidPrice = 0, bestAskPrice = 0;
            double theta = 0;

            if (marketData.containsKey("tbq"))
                tbq = getDouble(marketData.get("tbq"));
            if (marketData.containsKey("tsq"))
                tsq = getDouble(marketData.get("tsq"));
            if (marketData.containsKey("atp"))
                atp = getDouble(marketData.get("atp"));
            if (marketData.containsKey("vtt"))
                vtt = getLong(marketData.get("vtt"));
            if (marketData.containsKey("oi"))
                oi = getDouble(marketData.get("oi"));
            
            if (marketData.containsKey("optionGreeks")) {
                 Map<String, Object> greeks = (Map<String, Object>) marketData.get("optionGreeks");
                 if (greeks != null && greeks.containsKey("theta")) {
                     theta = getDouble(greeks.get("theta"));
                 }
            }

            if (marketData.containsKey("marketLevel")) {
                Map<String, Object> marketLevel = (Map<String, Object>) marketData.get("marketLevel");
                List<Map<String, Object>> bidAskQuote = (List<Map<String, Object>>) marketLevel.get("bidAskQuote");
                if (bidAskQuote != null && !bidAskQuote.isEmpty()) {
                    bestBidPrice = getDouble(bidAskQuote.get(0).get("bidP"));
                    bestAskPrice = getDouble(bidAskQuote.get(0).get("askP"));
                }
            }

            long sequence = ringBuffer.next();
            try {
                MarketEvent event = ringBuffer.get(sequence);
                event.setSymbol(instrumentKey);
                event.setLtp(ltp);
                event.setLtt(ltt);
                event.setLtq(ltq);
                event.setCp(cp);
                event.setTbq(tbq);
                event.setTsq(tsq);
                event.setVtt(vtt);
                event.setOi(oi);
                event.setIv(0);
                event.setAtp(atp);
                event.setTs(ltt);
                event.setBestBidPrice(bestBidPrice);
                event.setBestAskPrice(bestAskPrice);
                event.setTheta(theta);
            } finally {
                ringBuffer.publish(sequence);
            }

        } catch (Exception e) {
            System.err.println("Error mapping data: " + e.getMessage() + " on line " + gson.toJson(data));
            e.printStackTrace();
        }
    }

    private double getDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try {
                return Double.parseDouble((String) o);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private long getLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) {
            try {
                return Long.parseLong((String) o);
            } catch (NumberFormatException e) {
                 // Try parsing as double first in case format is "100.0"
                try {
                    return (long) Double.parseDouble((String) o);
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * Converts raw flat record into the nested "feeds" structure.
     */
    private Map<String, Object> transformRecord(Map<String, Object> raw) {
        String instrumentKey = (String) raw.get("instrumentKey");
        Object fullFeed = raw.get("fullFeed");

        // Inner object: contains fullFeed and requestMode
        Map<String, Object> innerData = new java.util.HashMap<>();
        innerData.put("fullFeed", fullFeed);
        innerData.put("requestMode", "full_d5");

        // Feeds map: instrumentKey -> innerData
        Map<String, Object> instrumentMap = new java.util.HashMap<>();
        instrumentMap.put(instrumentKey, innerData);

        // Root map: "feeds" -> instrumentMap
        Map<String, Object> root = new java.util.HashMap<>();
        root.put("feeds", instrumentMap);

        return root;
    }

}
