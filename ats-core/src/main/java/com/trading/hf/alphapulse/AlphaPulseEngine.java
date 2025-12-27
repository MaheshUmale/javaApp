package com.trading.hf.alphapulse;

import com.trading.hf.Candle;
import com.trading.hf.MarketEvent;
import com.trading.hf.InstrumentMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.trading.hf.SignalEvent;
import com.lmax.disruptor.RingBuffer;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class AlphaPulseEngine {

    private static final Logger logger = LoggerFactory.getLogger(AlphaPulseEngine.class);

    // Configuration
    private static final long MACRO_CANDLE_DURATION_MS = 50 * 60 * 1000;
    private static final int MACRO_CANDLE_WINDOW_SIZE = 20;
    private static final long MICRO_CANDLE_DURATION_MS = 5 * 60 * 1000;
    private static final long ALPHA_CALCULATION_INTERVAL_MS = 500;
    private static final int TRAP_COOL_OFF_MINUTES = 10;

    // Data Structures
    private final ConcurrentHashMap<String, Deque<Candle>> macroCandles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Candle>> microCandles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Zone> valueZones = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SymbolState> symbolStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAlphaCalcTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> priceActionTriggers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> trapCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> coolOffUntil = new ConcurrentHashMap<>();
    private final RingBuffer<SignalEvent> signalRingBuffer;
    private final InstrumentMaster instrumentMaster;
    private final String indexSymbol;

    public AlphaPulseEngine(RingBuffer<SignalEvent> signalRingBuffer, String indexSymbol, InstrumentMaster instrumentMaster) {
        this.signalRingBuffer = signalRingBuffer;
        this.indexSymbol = indexSymbol;
        this.instrumentMaster = instrumentMaster;
        logger.info("AlphaPulseEngine initialized for index: {}", indexSymbol);
    }

    public void onMarketEvent(MarketEvent event) {
        updateSymbolState(event);
        if (event.getSymbol().equals(indexSymbol)) {
            updateMacroView(event);
            updateMicroView(event);
        } else {
            calculateAlphaEfficiency(event);
            updateSentimentAndExecute();
        }
    }

    private void updateSymbolState(MarketEvent event) {
        symbolStates.putIfAbsent(event.getSymbol(), new SymbolState());
        symbolStates.get(event.getSymbol()).update(event);
    }

    private void calculateAlphaEfficiency(MarketEvent optionEvent) {
        long currentTime = System.currentTimeMillis();
        lastAlphaCalcTime.putIfAbsent(optionEvent.getSymbol(), 0L);

        if (currentTime - lastAlphaCalcTime.get(optionEvent.getSymbol()) < ALPHA_CALCULATION_INTERVAL_MS) {
            return;
        }

        SymbolState optionState = symbolStates.get(optionEvent.getSymbol());
        SymbolState indexState = symbolStates.get(this.indexSymbol);

        if (optionState == null || indexState == null || optionState.previousLtp == 0 || indexState.previousLtp == 0) {
            return;
        }

        double optionDelta = optionEvent.getOptionDelta();
        if (optionDelta == 0) return;

        double indexMove = indexState.currentLtp - indexState.previousLtp;
        double expectedMove = indexMove * optionDelta;
        double actualMove = optionState.currentLtp - optionState.previousLtp;

        double alpha = (expectedMove == 0) ? Double.POSITIVE_INFINITY * Math.signum(actualMove) : actualMove / expectedMove;
        optionState.alpha = alpha;

        if ((indexMove > 0 && alpha < 0.7) || (indexMove < 0 && alpha < 0.7)) {
            trapCount.merge(indexSymbol, 1, Integer::sum);
            if (trapCount.get(indexSymbol) >= 3) {
                coolOffUntil.put(indexSymbol, currentTime + TRAP_COOL_OFF_MINUTES * 60 * 1000);
                trapCount.put(indexSymbol, 0);
            }
        } else {
            trapCount.put(indexSymbol, 0);
        }

        lastAlphaCalcTime.put(optionEvent.getSymbol(), currentTime);
    }

    private void updateSentimentAndExecute() {
        Zone zone = valueZones.get(indexSymbol);
        if (zone == null || System.currentTimeMillis() < coolOffUntil.getOrDefault(indexSymbol, 0L)) {
            return;
        }

        SymbolState indexState = symbolStates.get(indexSymbol);
        if (indexState == null) return;

        double indexPrice = indexState.currentLtp;
        boolean inSupportZone = indexPrice >= zone.getSupport() && indexPrice <= zone.getSupport() * 1.005;
        boolean inResistanceZone = indexPrice <= zone.getResistance() && indexPrice >= zone.getResistance() * 0.995;

        if (priceActionTriggers.getOrDefault(indexSymbol, false)) {
            int atmStrike = (int) (Math.round(indexPrice / 50.0) * 50);

            Optional<LocalDate> expiry = instrumentMaster.findNearestExpiry(indexSymbol, LocalDate.now());
            if(expiry.isEmpty()) return;

            Optional<String> callSymbolOpt = instrumentMaster.findInstrumentKey(indexSymbol, atmStrike, "CE", expiry.get());
            Optional<String> putSymbolOpt = instrumentMaster.findInstrumentKey(indexSymbol, atmStrike, "PE", expiry.get());

            if(callSymbolOpt.isEmpty() || putSymbolOpt.isEmpty()) return;

            String callSymbol = callSymbolOpt.get();
            String putSymbol = putSymbolOpt.get();

            SymbolState callState = symbolStates.get(callSymbol);
            SymbolState putState = symbolStates.get(putSymbol);

            if (callState != null && putState != null) {
                if (inSupportZone) {
                    boolean sentimentConfirmed = putState.changeInOI > callState.changeInOI;
                    boolean alphaConfirmed = callState.alpha > 1.2;
                    boolean optionStructureConfirmed = isOptionAtSupport(callState.marketEvent);

                    if(sentimentConfirmed && alphaConfirmed && optionStructureConfirmed) {
                         logger.info("EXECUTE CALL BUY SIGNAL: All conditions met.");
                         publishSignal(callSymbol, "BUY", callState.currentLtp);
                         priceActionTriggers.put(indexSymbol, false);
                    }
                } else if (inResistanceZone) {
                    boolean sentimentConfirmed = callState.changeInOI > putState.changeInOI;
                    boolean alphaConfirmed = putState.alpha > 1.2;
                    boolean optionStructureConfirmed = isOptionAtSupport(putState.marketEvent);

                    if(sentimentConfirmed && alphaConfirmed && optionStructureConfirmed) {
                         logger.info("EXECUTE PUT BUY SIGNAL: All conditions met.");
                         publishSignal(putSymbol, "BUY", putState.currentLtp);
                         priceActionTriggers.put(indexSymbol, false);
                    }
                }
            }
        }
    }

    private boolean isOptionAtSupport(MarketEvent optionEvent) {
        if(optionEvent == null) return false;
        Deque<Candle> optionCandles = microCandles.get(optionEvent.getSymbol());
        if (optionCandles == null || optionCandles.isEmpty()) {
            return false;
        }
        Candle currentCandle = optionCandles.getLast();
        return optionEvent.getLtp() <= currentCandle.getLow() * 1.005;
    }

    private void updateMacroView(MarketEvent event) {
        String symbol = event.getSymbol();
        macroCandles.putIfAbsent(symbol, new ArrayDeque<>());
        Deque<Candle> candles = macroCandles.get(symbol);

        long candleTimestamp = event.getTs() - (event.getTs() % MACRO_CANDLE_DURATION_MS);

        if (candles.isEmpty() || candles.getLast().getTimestamp() != candleTimestamp) {
            Candle newCandle = new Candle(candleTimestamp, event.getLtp(), event.getLtp(), event.getLtp(), event.getLtp(), event.getLtq());
            candles.addLast(newCandle);
            if (candles.size() > MACRO_CANDLE_WINDOW_SIZE) {
                candles.removeFirst();
            }
        } else {
            candles.getLast().update(event.getLtp(), event.getLtq());
        }

        if (candles.size() == MACRO_CANDLE_WINDOW_SIZE) {
            calculateValueZones(symbol, candles);
        }
    }

    private void updateMicroView(MarketEvent event) {
        String symbol = event.getSymbol();
        microCandles.putIfAbsent(symbol, new ArrayDeque<>());
        Deque<Candle> candles = microCandles.get(symbol);
        long candleTimestamp = event.getTs() - (event.getTs() % MICRO_CANDLE_DURATION_MS);

        if (candles.isEmpty() || candles.getLast().getTimestamp() != candleTimestamp) {
            candles.addLast(new Candle(candleTimestamp, event.getLtp(), event.getLtp(), event.getLtp(), event.getLtp(), event.getLtq()));
        } else {
            candles.getLast().update(event.getLtp(), event.getLtq());
        }

        if (candles.size() > 2) {
            candles.removeFirst();
        }

        Zone zone = valueZones.get(symbol);
        if (zone != null && ((event.getLtp() >= zone.getSupport() && event.getLtp() <= zone.getResistance()) || (event.getLtp() <= zone.getResistance() && event.getLtp() >= zone.getResistance() * 0.995)) && candles.size() == 2) {
            detectPriceActionTriggers(symbol, candles.getFirst(), candles.getLast());
        } else {
            priceActionTriggers.put(symbol, false);
        }
    }

    private void detectPriceActionTriggers(String symbol, Candle previous, Candle current) {
        if (isHammer(current) || isEngulfing(previous, current) || isRejectionWick(current)) {
             priceActionTriggers.put(symbol, true);
        }
    }

    private void publishSignal(String symbol, String type, double price) {
        if (signalRingBuffer == null) return;
        long sequence = signalRingBuffer.next();
        try {
            SignalEvent event = signalRingBuffer.get(sequence);
            event.set(symbol, type, price, 0, 0, 0, 0, System.currentTimeMillis());
        } finally {
            signalRingBuffer.publish(sequence);
        }
    }

    private boolean isHammer(Candle candle) {
        double body = Math.abs(candle.getOpen() - candle.getClose());
        double lowerWick = (candle.getOpen() > candle.getClose() ? candle.getClose() : candle.getOpen()) - candle.getLow();
        double upperWick = candle.getHigh() - (candle.getOpen() > candle.getClose() ? candle.getOpen() : candle.getClose());
        return lowerWick > body * 2 && upperWick < body;
    }

    private boolean isEngulfing(Candle previous, Candle current) {
        return (current.getClose() > previous.getOpen() && current.getOpen() < previous.getClose() && current.getClose() > previous.getHigh() && current.getOpen() < previous.getLow()) ||
               (current.getOpen() > previous.getClose() && current.getClose() < previous.getOpen() && current.getOpen() > previous.getHigh() && current.getClose() < previous.getLow());
    }

    private boolean isRejectionWick(Candle candle) {
        double body = Math.abs(candle.getOpen() - candle.getClose());
        double upperWick = candle.getHigh() - Math.max(candle.getOpen(), candle.getClose());
        double lowerWick = Math.min(candle.getOpen(), candle.getClose()) - candle.getLow();
        return upperWick > body * 2 || lowerWick > body * 2;
    }

    private void calculateValueZones(String symbol, Deque<Candle> candles) {
        double highestHigh = candles.stream().mapToDouble(Candle::getHigh).max().orElse(Double.MIN_VALUE);
        double lowestLow = candles.stream().mapToDouble(Candle::getLow).min().orElse(Double.MAX_VALUE);
        valueZones.put(symbol, new Zone(highestHigh, lowestLow));
    }

    public static class Zone {
        private final double resistance, support;
        public Zone(double resistance, double support) { this.resistance = resistance; this.support = support; }
        public double getResistance() { return resistance; }
        public double getSupport() { return support; }
    }

    private static class SymbolState {
        double previousLtp, currentLtp, currentOI, previousOI, changeInOI, alpha;
        long lastUpdateTime;
        MarketEvent marketEvent;
        void update(MarketEvent event) {
            this.marketEvent = event;
            this.previousLtp = this.currentLtp;
            this.currentLtp = event.getLtp();
            this.lastUpdateTime = event.getTs();
            this.previousOI = this.currentOI;
            this.currentOI = event.getOi();
            if(this.previousOI > 0) {
                this.changeInOI = this.currentOI - this.previousOI;
            }
        }
    }
}
