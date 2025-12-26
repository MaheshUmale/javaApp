package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class IndexWeightCalculator implements EventHandler<MarketEvent> {

    private static final Logger logger = LoggerFactory.getLogger(IndexWeightCalculator.class);
    private final Map<String, Heavyweight> heavyweights;
    private volatile double aggregateWeightedDelta = 0.0;
    private com.lmax.disruptor.RingBuffer<HeavyweightEvent> heavyweightRingBuffer;

    public IndexWeightCalculator(String indexPath, InstrumentMaster instrumentMaster) {
        this.heavyweights = loadWeights(indexPath, instrumentMaster);
    }

    public void setHeavyweightRingBuffer(com.lmax.disruptor.RingBuffer<HeavyweightEvent> heavyweightRingBuffer) {
        this.heavyweightRingBuffer = heavyweightRingBuffer;
    }

    @Override
    public void onEvent(MarketEvent event, long sequence, boolean endOfBatch) {
        Heavyweight hw = heavyweights.get(event.getSymbol());
        if (hw != null) {
            double delta = (event.getTbq() - event.getTsq());
            logger.debug("[IndexWeight] Event for heavyweight {}: tbq={}, tsq={}, ltp={}, dayOpen={}", 
                    event.getSymbol(), event.getTbq(), event.getTsq(), event.getLtp(), event.getDayOpen());
            
            // Fallback: If Order Book delta is 0 (missing data), use Price Change Proxy
            // Directional Logic: If Close > Open, assume Buying info.
            if (delta == 0 && event.getLtp() != 0) {
                 double open = event.getDayOpen(); // assuming open is available, or use prev ltp
                 if (open == 0) open = hw.getLtp(); // use last ltp
                 
                 // Proxy Delta: Price Change * Weight * 1000 (Arbitrary scaling for visibility)
                 if (event.getLtp() > open) delta = 10000; 
                 else if (event.getLtp() < open) delta = -10000;
            }

            hw.setDelta(delta);
            hw.setLtp(event.getLtp());
            updateAggregateWeightedDelta();

                logger.debug("[IndexWeight] Updated heavyweight {} -> delta={}, ltp={}, aggregateWeightedDelta={}", 
                    event.getSymbol(), hw.getDelta(), hw.getLtp(), aggregateWeightedDelta);

            if (heavyweightRingBuffer != null) {
                long nextSeq = heavyweightRingBuffer.next();
                try {
                    HeavyweightEvent hwe = heavyweightRingBuffer.get(nextSeq);
                    hwe.set(event.getSymbol(), event.getLtp(), hw.getWeight(), delta, aggregateWeightedDelta, System.currentTimeMillis());
                } finally {
                    heavyweightRingBuffer.publish(nextSeq);
                }
            }
        }
        else {
            logger.debug("[IndexWeight] Received market event for non-heavyweight symbol: {}", event.getSymbol());
        }
    }

    private void updateAggregateWeightedDelta() {
        aggregateWeightedDelta = heavyweights.values().stream()
                .mapToDouble(hw -> hw.getDelta() * hw.getWeight())
                .sum();
    }

    public double getAggregateWeightedDelta() {
        return aggregateWeightedDelta;
    }

    public Map<String, Heavyweight> getHeavyweights() {
        return heavyweights;
    }

    public java.util.Set<String> getInstrumentKeys() {
        return heavyweights.values().stream().map(Heavyweight::getInstrumentKey).collect(Collectors.toSet());
    }

    private Map<String, Heavyweight> loadWeights(String path, InstrumentMaster instrumentMaster) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, List<Map<String, Object>>>>() {
        }.getType();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Cannot find resource: " + path);
            }
            Map<String, List<Map<String, Object>>> rawData = gson
                    .fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), type);
            List<Map<String, Object>> nifty50List = rawData.get("NIFTY50");

            Map<String, Heavyweight> tempMap = new ConcurrentHashMap<>();
            for (Map<String, Object> entry : nifty50List) {
                int rank = ((Number) entry.get("rank")).intValue();
                String tradingSymbol = (String) entry.get("symbol");
                String companyName = (String) entry.get("name");
                double weightValue = ((Number) entry.get("weight")).doubleValue();
                String sector = (String) entry.get("sector");

                instrumentMaster.findInstrumentKeyForEquity(tradingSymbol).ifPresentOrElse(
                        instrumentKey -> tempMap.put(instrumentKey,
                                new Heavyweight(rank, tradingSymbol, companyName, weightValue, sector, instrumentKey)),
                        () -> logger.warn("Could not find instrument key for equity: {}", tradingSymbol));
            }
            return tempMap;

        } catch (Exception e) {
            throw new RuntimeException("Failed to load index weights", e);
        }
    }

    public static class Heavyweight {
        private final int rank;
        private final String name;
        private final String companyName;
        private final double weight;
        private final String sector;
        private final String instrumentKey;
        private volatile double delta;

        public Heavyweight(int rank, String name, String companyName, double weight, String sector,
                String instrumentKey) {
            this.rank = rank;
            this.name = name;
            this.companyName = companyName;
            this.weight = weight;
            this.sector = sector;
            this.instrumentKey = instrumentKey;
            this.delta = 0.0;
            this.ltp = 0.0;
        }

        public int getRank() {
            return rank;
        }

        public String getName() {
            return name;
        }

        public String getCompanyName() {
            return companyName;
        }

        public double getWeight() {
            return weight;
        }

        public String getSector() {
            return sector;
        }

        public String getInstrumentKey() {
            return instrumentKey;
        }

        public double getDelta() {
            return delta;
        }

        public void setDelta(double delta) {
            this.delta = delta;
        }

        private volatile double ltp;

        public double getLtp() {
            return ltp;
        }

        public void setLtp(double ltp) {
            this.ltp = ltp;
        }
    }
}
