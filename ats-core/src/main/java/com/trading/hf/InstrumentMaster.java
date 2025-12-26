package com.trading.hf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.annotations.SerializedName;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstrumentMaster {
    private static final Logger logger = LoggerFactory.getLogger(InstrumentMaster.class);

    private final Map<String, List<InstrumentDefinition>> underlyingMap = new ConcurrentHashMap<>();
    private final Map<String, InstrumentDefinition> instrumentKeyMap = new ConcurrentHashMap<>();
    private final Map<String, String> tradingSymbolToInstrumentKeyMap = new ConcurrentHashMap<>();
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public InstrumentMaster(String resourcePath) {
        loadInstruments(resourcePath);
    }

    private void loadInstruments(String resourcePath) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<InstrumentDefinition>>() {
        }.getType();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Cannot find resource: " + resourcePath);
            }
            List<InstrumentDefinition> instruments = gson.fromJson(new InputStreamReader(is), listType);
            for (InstrumentDefinition instrument : instruments) {
                instrumentKeyMap.put(instrument.getInstrumentKey(), instrument);
                // Index by underlying key (e.g., NSE_INDEX|Nifty 50) and asset symbol (e.g.,
                // NIFTY)
                if (instrument.getUnderlyingKey() != null) {
                    underlyingMap
                            .computeIfAbsent(instrument.getUnderlyingKey(), k -> new java.util.ArrayList<>())
                            .add(instrument);
                }
                if (instrument.assetSymbol != null && !instrument.assetSymbol.isEmpty()) {
                    underlyingMap
                            .computeIfAbsent(instrument.assetSymbol, k -> new java.util.ArrayList<>())
                            .add(instrument);
                }

                if (instrument.getTradingSymbol() != null) {
                    // Strict filtering for equities as per user requirement:
                    // segment == 'NSE_EQ' AND instrument_type == 'EQ'
                    if ("NSE_EQ".equalsIgnoreCase(instrument.getSegment()) && "EQ".equalsIgnoreCase(instrument.getInstrumentType())) {
                        tradingSymbolToInstrumentKeyMap.put(instrument.getTradingSymbol(), instrument.getInstrumentKey());
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load instrument master", e);
        }
    }

    public void addInstrumentKey(String symbol, String key) {
        tradingSymbolToInstrumentKeyMap.put(symbol, key);
    }

    public void updateFromAutoManager(AutoInstrumentManager manager) {
        // This could be used to override or supplement the mapping
        // For example, if we have specific keys extracted daily
    }

    public void addInstrumentDefinitions(List<InstrumentDefinition> instruments) {
        for (InstrumentDefinition instrument : instruments) {
            instrumentKeyMap.put(instrument.getInstrumentKey(), instrument);
            
            if (instrument.getUnderlyingKey() != null) {
                underlyingMap
                        .computeIfAbsent(instrument.getUnderlyingKey(), k -> new java.util.ArrayList<>())
                        .add(instrument);
            }
            if (instrument.assetSymbol != null && !instrument.assetSymbol.isEmpty()) {
                underlyingMap
                        .computeIfAbsent(instrument.assetSymbol, k -> new java.util.ArrayList<>())
                        .add(instrument);
            }

            if (instrument.getTradingSymbol() != null) {
                if ("NSE_EQ".equalsIgnoreCase(instrument.getSegment()) && "EQ".equalsIgnoreCase(instrument.getInstrumentType())) {
                    tradingSymbolToInstrumentKeyMap.put(instrument.getTradingSymbol(), instrument.getInstrumentKey());
                }
            }
        }
        logger.info("Dynamically added {} instruments to InstrumentMaster", instruments.size());
    }

    public Optional<InstrumentDefinition> getInstrument(String instrumentKey) {
        return Optional.ofNullable(instrumentKeyMap.get(instrumentKey));
    }

    public Optional<String> findInstrumentKey(String underlying, int strike, String optionType, LocalDate expiry) {
        return underlyingMap.getOrDefault(underlying, List.of()).stream()
                .filter(inst -> Math.abs(inst.getStrikePrice() - strike) < 0.01)
                .filter(inst -> inst.getOptionType() != null && inst.getOptionType().equalsIgnoreCase(optionType))
                .filter(inst -> inst.getExpiry().isEqual(expiry))
                .map(InstrumentDefinition::getInstrumentKey)
                .findFirst();
    }

    public Optional<String> findInstrumentKeyForEquity(String tradingSymbol) {
        return Optional.ofNullable(tradingSymbolToInstrumentKeyMap.get(tradingSymbol));
    }

    public Optional<LocalDate> findNearestExpiry(String underlying, LocalDate date) {
        return underlyingMap.getOrDefault(underlying, List.of()).stream()
                .map(InstrumentDefinition::getExpiry)
                .filter(expiry -> expiry != null && !expiry.isBefore(date))
                .min(Comparator.naturalOrder());
    }

    public static class InstrumentDefinition {
        @SerializedName("instrument_key")
        private String instrumentKey;
        @SerializedName("underlying_key")
        private String underlyingKey;
        @SerializedName("trading_symbol")
        private String tradingSymbol;
        @SerializedName("asset_symbol")
        private String assetSymbol;
        @SerializedName("strike_price")
        private Double strikePrice;
        @SerializedName("instrument_type")
        private String instrumentType;
        private String segment;
        private String expiry;

        public String getInstrumentKey() {
            return instrumentKey;
        }

        public String getSegment() {
            return segment;
        }

        public String getInstrumentType() {
            return instrumentType;
        }

        public String getUnderlyingKey() {
            return underlyingKey;
        }

        public String getTradingSymbol() {
            return tradingSymbol;
        }

        public double getStrikePrice() {
            return strikePrice == null ? 0.0 : strikePrice;
        }

        public String getOptionType() {
            return instrumentType;
        }

        public LocalDate getExpiry() {
            if (expiry == null || expiry.isEmpty())
                return null;
            try {
                if (expiry.matches("\\d+")) {
                    return java.time.Instant.ofEpochMilli(Long.parseLong(expiry))
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
                }
                return LocalDate.parse(expiry, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
