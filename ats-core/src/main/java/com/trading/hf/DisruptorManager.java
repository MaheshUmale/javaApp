package com.trading.hf;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.EventHandler;
import java.util.concurrent.ThreadFactory;
import java.util.ArrayList;
import java.util.List;

public class DisruptorManager {

    private final Disruptor<MarketEvent> marketEventDisruptor;
    private RingBuffer<MarketEvent> marketEventRingBuffer;

    private final Disruptor<RawFeedEvent> rawFeedDisruptor;
    private final RingBuffer<RawFeedEvent> rawFeedRingBuffer;

    private final Disruptor<SignalEvent> signalDisruptor;
    private final RingBuffer<SignalEvent> signalRingBuffer;

    private final Disruptor<OrderEvent> orderDisruptor;
    private final RingBuffer<OrderEvent> orderRingBuffer;

    private final Disruptor<TelemetryEvent> telemetryDisruptor;
    private final RingBuffer<TelemetryEvent> telemetryRingBuffer;

    private final Disruptor<HeavyweightEvent> heavyweightDisruptor;
    private final RingBuffer<HeavyweightEvent> heavyweightRingBuffer;

    private final List<EventHandler<MarketEvent>> marketEventHandlers;

    @SuppressWarnings("unchecked")
    public DisruptorManager(
            QuestDBWriter questDBWriter,
            RawFeedWriter rawFeedWriter,
            List<EventHandler<MarketEvent>> marketEventHandlers,
            IndexWeightCalculator indexWeightCalculator,
            OptionChainProvider optionChainProvider,
            ThetaExitGuard thetaExitGuard,
            SignalPersistenceWriter signalPersistenceWriter,
            OrderPersistenceWriter orderPersistenceWriter,
            TelemetryWriter telemetryWriter,
            HeavyweightWriter heavyweightWriter,
            List<EventHandler<MarketEvent>> extraHandlers,
            PaperTradingEngine paperTradingEngine) {

        this.marketEventHandlers = marketEventHandlers;
        ThreadFactory threadFactory = Thread.ofVirtual().factory();
        WaitStrategy waitStrategy = getWaitStrategy();

        marketEventDisruptor = new Disruptor<>(
                MarketEvent.EVENT_FACTORY,
                65536,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);


        this.marketEventHandlers.add(indexWeightCalculator);
        this.marketEventHandlers.add(optionChainProvider);
        if (thetaExitGuard != null) {
            this.marketEventHandlers.add(thetaExitGuard);
        }
        if (questDBWriter != null) {
            this.marketEventHandlers.add(questDBWriter);
        }
        if (extraHandlers != null) {
            this.marketEventHandlers.addAll(extraHandlers);
        }
        this.marketEventHandlers.add((event, seq, end) -> {
            publishTelemetry("MARKET_PROCESSOR", marketEventDisruptor.getRingBuffer().remainingCapacity(),
                    System.currentTimeMillis() - event.getTs(),
                    System.currentTimeMillis() - event.getLtt());
        });

        rawFeedDisruptor = new Disruptor<>(
                RawFeedEvent::new,
                65536,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);

        if (rawFeedWriter != null) {
            rawFeedDisruptor.handleEventsWith(rawFeedWriter);
        }
        rawFeedDisruptor.handleEventsWith((event, seq, end) -> {
            publishTelemetry("RAW_FEED_PROCESSOR", rawFeedDisruptor.getRingBuffer().remainingCapacity(),
                    System.currentTimeMillis() - event.getTimestamp(), 0);
        });
        rawFeedRingBuffer = rawFeedDisruptor.start();

        signalDisruptor = new Disruptor<>(
                SignalEvent.EVENT_FACTORY,
                16384,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);

        List<EventHandler<SignalEvent>> signalHandlers = new ArrayList<>();
        if (signalPersistenceWriter != null) {
            signalHandlers.add(signalPersistenceWriter);
        }
        if (paperTradingEngine != null) {
            signalHandlers.add(paperTradingEngine);
        }
        if (!signalHandlers.isEmpty()) {
            signalDisruptor.handleEventsWith(signalHandlers.toArray(new EventHandler[0]));
        }
        signalRingBuffer = signalDisruptor.start();

        orderDisruptor = new Disruptor<>(
                OrderEvent.EVENT_FACTORY,
                8192,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);
        if (orderPersistenceWriter != null) {
            orderDisruptor.handleEventsWith(orderPersistenceWriter);
        }
        orderRingBuffer = orderDisruptor.start();

        telemetryDisruptor = new Disruptor<>(
                TelemetryEvent.EVENT_FACTORY,
                4096,
                threadFactory,
                ProducerType.MULTI,
                waitStrategy);
        if (telemetryWriter != null) {
            telemetryDisruptor.handleEventsWith(telemetryWriter);
        }
        telemetryRingBuffer = telemetryDisruptor.start();

        heavyweightDisruptor = new Disruptor<>(
                HeavyweightEvent.EVENT_FACTORY,
                16384,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);
        if (heavyweightWriter != null) {
            heavyweightDisruptor.handleEventsWith(heavyweightWriter);
        }
        heavyweightRingBuffer = heavyweightDisruptor.start();
    }

    public void start() {
        marketEventDisruptor.handleEventsWith(this.marketEventHandlers.toArray(new EventHandler[0]));
        this.marketEventRingBuffer = marketEventDisruptor.start();
    }

    private void publishTelemetry(String processor, long capacity, long procLag, long netLag) {
        if (telemetryRingBuffer == null) return;
        long sequence = telemetryRingBuffer.next();
        try {
            TelemetryEvent event = telemetryRingBuffer.get(sequence);
            event.set(processor, capacity, procLag, Math.max(0, netLag), System.currentTimeMillis());
        } finally {
            telemetryRingBuffer.publish(sequence);
        }
    }

    public RingBuffer<MarketEvent> getMarketEventRingBuffer() { return marketEventRingBuffer; }
    public RingBuffer<RawFeedEvent> getRawFeedRingBuffer() { return rawFeedRingBuffer; }
    public RingBuffer<SignalEvent> getSignalRingBuffer() { return signalRingBuffer; }
    public RingBuffer<OrderEvent> getOrderRingBuffer() { return orderRingBuffer; }
    public RingBuffer<TelemetryEvent> getTelemetryRingBuffer() { return telemetryRingBuffer; }
    public RingBuffer<HeavyweightEvent> getHeavyweightRingBuffer() { return heavyweightRingBuffer; }

    public void shutdown() {
        marketEventDisruptor.shutdown();
        rawFeedDisruptor.shutdown();
        signalDisruptor.shutdown();
        orderDisruptor.shutdown();
        telemetryDisruptor.shutdown();
        heavyweightDisruptor.shutdown();
    }

    private WaitStrategy getWaitStrategy() {
        String strategyName = ConfigLoader.getProperty("disruptor.wait.strategy", "sleeping").toLowerCase();
        return switch (strategyName) {
            case "blocking" -> new BlockingWaitStrategy();
            case "yielding" -> new YieldingWaitStrategy();
            case "busyspin" -> new com.lmax.disruptor.BusySpinWaitStrategy();
            default -> new SleepingWaitStrategy();
        };
    }
}
