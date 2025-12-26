package com.trading.hf;

import com.trading.hf.VolumeBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionProfileCalculator {
    private static final Logger logger = LoggerFactory.getLogger(AuctionProfileCalculator.class);

    private static final java.util.concurrent.atomic.AtomicInteger instanceCounter = new java.util.concurrent.atomic.AtomicInteger(0);
    private final int instanceId = instanceCounter.incrementAndGet();
    private final Map<String, MarketProfile> profiles = new ConcurrentHashMap<>();

    public AuctionProfileCalculator() {
        logger.info("!!! [AuctionProfile] New Instance Created: ID={}, Thread={} !!!", instanceId, Thread.currentThread().getName());
    }

    public void onVolumeBar(VolumeBar volumeBar) {
        logger.info("!!! [AuctionProfile] onVolumeBar CALLED for: {} !!!", volumeBar.getSymbol());
        MarketProfile profile = profiles.computeIfAbsent(volumeBar.getSymbol(), k -> {
            logger.info("!!! [AuctionProfile] Creating NEW profile for: {} !!!", k);
            return new MarketProfile();
        });
        synchronized (profile) {
            profile.addVolume(volumeBar.getClose(), volumeBar.getVolume());
            profile.calculateValueArea();
            logger.info("[AuctionProfile] Updated for {}: VAH={}, POC={}, VAL={}, TotalVol={}",
                volumeBar.getSymbol(), profile.getVah(), profile.getPoc(), profile.getVal(), profile.getTotalVolume());
        }
    }

    public MarketProfile getProfile(String symbol) {
        logger.info("[AuctionProfile] [ID={}] getProfile called for: {}, profiles map size: {}, keys: {}", 
            instanceId, symbol, profiles.size(), profiles.keySet());

        MarketProfile profile = profiles.get(symbol);
        if (profile != null) {
            synchronized (profile) {
                // Return a copy to ensure thread safety for the caller
                return new MarketProfile(profile);
            }
        }

        logger.warn("[AuctionProfile] Profile NOT FOUND for: {}.", symbol);
        // Do NOT create an empty profile here — return null so callers can detect
        // that no profile data exists yet for the requested symbol.
        return null;
    }
    public static class MarketProfile {
        private final TreeMap<Double, Long> volumeAtPrice = new TreeMap<>();
        private double poc;
        private double vah;
        private double val;
        private long totalVolume;

        // Default constructor
        public MarketProfile() {
        }

        // Copy constructor for thread-safe snapshots
        public MarketProfile(MarketProfile other) {
            this.volumeAtPrice.putAll(other.volumeAtPrice);
            this.poc = other.poc;
            this.vah = other.vah;
            this.val = other.val;
            this.totalVolume = other.totalVolume;
        }

        public void addVolume(double price, long volume) {
            volumeAtPrice.put(price, volumeAtPrice.getOrDefault(price, 0L) + volume);
            totalVolume += volume;
        }

        public void calculateValueArea() {
            if (volumeAtPrice.isEmpty()) {
                return;
            }

            // Find POC
            poc = Collections.max(volumeAtPrice.entrySet(), Map.Entry.comparingByValue()).getKey();

            long vaVolume = (long) (totalVolume * 0.70);
            long currentVolume = volumeAtPrice.get(poc);

            // Expand around POC
            Map.Entry<Double, Long> lowerEntry = volumeAtPrice.lowerEntry(poc);
            Map.Entry<Double, Long> higherEntry = volumeAtPrice.higherEntry(poc);

            vah = poc;
            val = poc;

            while (currentVolume < vaVolume) {
                if (higherEntry == null && lowerEntry == null) {
                    break;
                }

                if (higherEntry != null && (lowerEntry == null || higherEntry.getValue() >= lowerEntry.getValue())) {
                    currentVolume += higherEntry.getValue();
                    vah = higherEntry.getKey();
                    higherEntry = volumeAtPrice.higherEntry(vah);
                } else if (lowerEntry != null) {
                    currentVolume += lowerEntry.getValue();
                    val = lowerEntry.getKey();
                    lowerEntry = volumeAtPrice.lowerEntry(val);
                }
            }
        }

        public double getPoc() {
            return poc;
        }

        public double getVah() {
            return vah;
        }

        public double getVal() {
            return val;
        }

        public TreeMap<Double, Long> getVolumeAtPrice() {
            return volumeAtPrice;
        }

        public long getTotalVolume() {
            return totalVolume;
        }
    }
}
