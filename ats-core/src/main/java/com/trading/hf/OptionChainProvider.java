package com.trading.hf;

import com.lmax.disruptor.EventHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.Comparator;

public class OptionChainProvider implements EventHandler<MarketEvent> {

    private final Map<String, MarketEvent> optionState = new ConcurrentHashMap<>();
    private final Map<String, Double> sessionInitialOi = new ConcurrentHashMap<>();
    private final AtomicReference<Double> spotPrice = new AtomicReference<>(0.0);
    private final InstrumentMaster instrumentMaster;
    private final String indexInstrumentKey;
    private final String indexSpotSymbol;
    private static final int STRIKE_DIFFERENCE = 50;
    private static final int WINDOW_SIZE = 4; // ATM +/- 2 strikes

    public OptionChainProvider(InstrumentMaster instrumentMaster, String indexInstrumentKey, String indexSpotSymbol) {
        this.instrumentMaster = instrumentMaster;
        this.indexInstrumentKey = indexInstrumentKey;
        this.indexSpotSymbol = indexSpotSymbol;
    }

    public OptionChainProvider(InstrumentMaster instrumentMaster) {
        this(instrumentMaster, "NSE_INDEX|Nifty 50", "NIFTY 50");
    }

    @Override
    public void onEvent(MarketEvent event, long sequence, boolean endOfBatch) {
        String symbol = event.getSymbol();
        if (symbol == null)
            return;

        if (indexInstrumentKey.equals(symbol) || indexSpotSymbol.equals(symbol)) {
            spotPrice.set(event.getLtp());
        } else {
            // Check if it's an option via instrument master
            instrumentMaster.getInstrument(symbol).ifPresent(inst -> {
                if ("CE".equalsIgnoreCase(inst.getOptionType()) || "PE".equalsIgnoreCase(inst.getOptionType())) {
                    optionState.put(symbol, event);
                    // Initialize session start OI if not already present
                    sessionInitialOi.putIfAbsent(symbol, event.getOi());
                }
            });
        }
    }

    public List<OptionChainDto> getOptionChainWindow() {
        double currentSpot = spotPrice.get();
        if (currentSpot == 0.0) {
            return List.of();
        }

        int atmStrike = (int) (Math.round(currentSpot / STRIKE_DIFFERENCE) * STRIKE_DIFFERENCE);

        return optionState.values().stream()
                .map(event -> {
                    InstrumentMaster.InstrumentDefinition inst = instrumentMaster.getInstrument(event.getSymbol())
                            .orElse(null);
                    if (inst == null)
                        return null;

                    int strike = (int) inst.getStrikePrice();
                    int lowerBound = atmStrike - (WINDOW_SIZE * STRIKE_DIFFERENCE);
                    int upperBound = atmStrike + (WINDOW_SIZE * STRIKE_DIFFERENCE);

                    if (strike >= lowerBound && strike <= upperBound) {
                        double currentOi = event.getOi();
                        double baseOi = sessionInitialOi.getOrDefault(event.getSymbol(), currentOi);
                        double oiChangePercent = (baseOi == 0) ? 0 : ((currentOi - baseOi) / baseOi) * 100;

                        return new OptionChainDto(
                                strike,
                                inst.getOptionType(),
                                event.getLtp(),
                                (long) currentOi,
                                oiChangePercent,
                                "NEUTRAL");
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(OptionChainDto::getStrike))
                .collect(Collectors.toList());
    }

    public double getPcr() {
        double callOi = 0;
        double putOi = 0;
        for (MarketEvent event : optionState.values()) {
            InstrumentMaster.InstrumentDefinition inst = instrumentMaster.getInstrument(event.getSymbol()).orElse(null);
            if (inst == null)
                continue;
            if ("CE".equalsIgnoreCase(inst.getOptionType())) {
                callOi += event.getOi();
            } else if ("PE".equalsIgnoreCase(inst.getOptionType())) {
                putOi += event.getOi();
            }
        }
        return (callOi == 0.0) ? 0 : putOi / callOi;
    }
}
