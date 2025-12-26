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
    private final RingBuffer<MarketEvent> marketEventRingBuffer;

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

    @SuppressWarnings("unchecked")
    public DisruptorManager(
            QuestDBWriter questDBWriter,
            RawFeedWriter rawFeedWriter,
            VolumeBarGenerator volumeBarGenerator,
            IndexWeightCalculator indexWeightCalculator,
            OptionChainProvider optionChainProvider,
            ThetaExitGuard thetaExitGuard,
            SignalPersistenceWriter signalPersistenceWriter,
            OrderPersistenceWriter orderPersistenceWriter,
            TelemetryWriter telemetryWriter,
            HeavyweightWriter heavyweightWriter,
            List<EventHandler<MarketEvent>> extraHandlers,
            PaperTradingEngine paperTradingEngine) {
        ThreadFactory threadFactory = Thread.ofVirtual().factory();

        WaitStrategy waitStrategy = getWaitStrategy();

        // Disruptor for processed MarketEvents
        marketEventDisruptor = new Disruptor<>(
                MarketEvent.EVENT_FACTORY,
                65536,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);

        List<EventHandler<MarketEvent>> marketEventHandlers = new ArrayList<>();
        marketEventHandlers.add(volumeBarGenerator);
        marketEventHandlers.add(indexWeightCalculator);
        marketEventHandlers.add(optionChainProvider);
        if (thetaExitGuard != null) {
            marketEventHandlers.add(thetaExitGuard);
        }
        if (questDBWriter != null) {
            marketEventHandlers.add(questDBWriter);
        }
        if (extraHandlers != null) {
            marketEventHandlers.addAll(extraHandlers);
        }

        // Instrumentation: Add Telemetry handler
        marketEventHandlers.add((event, seq, end) -> {
            publishTelemetry("MARKET_PROCESSOR", marketEventDisruptor.getRingBuffer().remainingCapacity(), 
                    System.currentTimeMillis() - event.getTs(), 
                    System.currentTimeMillis() - event.getLtt());
        });

        marketEventDisruptor.handleEventsWith(marketEventHandlers.toArray(new EventHandler[0]));
        marketEventRingBuffer = marketEventDisruptor.start();

        // Disruptor for RawFeedEvents
        rawFeedDisruptor = new Disruptor<>(
                RawFeedEvent::new,
                65536,
                threadFactory,
                ProducerType.SINGLE,
                waitStrategy);

        if (rawFeedWriter != null) {
            rawFeedDisruptor.handleEventsWith(rawFeedWriter);
        }

        // Instrumentation: Add Telemetry handler
        rawFeedDisruptor.handleEventsWith((event, seq, end) -> {
            publishTelemetry("RAW_FEED_PROCESSOR", rawFeedDisruptor.getRingBuffer().remainingCapacity(), 
                    System.currentTimeMillis() - event.getTimestamp(), 0);
        });

        rawFeedRingBuffer = rawFeedDisruptor.start();

        // Disruptor for SignalEvents
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

        // Disruptor for OrderEvents
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

        // Disruptor for TelemetryEvents
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

        // Disruptor for HeavyweightEvents
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

    public RingBuffer<MarketEvent> getMarketEventRingBuffer() {
        return marketEventRingBuffer;
    }

    public RingBuffer<RawFeedEvent> getRawFeedRingBuffer() {
        return rawFeedRingBuffer;
    }

    public RingBuffer<SignalEvent> getSignalRingBuffer() {
        return signalRingBuffer;
    }

    public RingBuffer<OrderEvent> getOrderRingBuffer() {
        return orderRingBuffer;
    }

    public RingBuffer<TelemetryEvent> getTelemetryRingBuffer() {
        return telemetryRingBuffer;
    }

    public RingBuffer<HeavyweightEvent> getHeavyweightRingBuffer() {
        return heavyweightRingBuffer;
    }

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
            // BusySpin is too aggressive for 90% of local users, but keeping as option
            case "busyspin" -> new com.lmax.disruptor.BusySpinWaitStrategy();
            default -> new SleepingWaitStrategy();
        };
    }
}
