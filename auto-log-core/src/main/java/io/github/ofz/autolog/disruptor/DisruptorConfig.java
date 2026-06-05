package io.github.ofz.autolog.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.github.ofz.autolog.formatter.LogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Configuration and lifecycle management for the LMAX Disruptor instance
 * powering asynchronous auto-logging.
 *
 * <p>Creates a single Disruptor with a {@link LogEventHandler} consumer chain,
 * and exposes a {@link LogEventProducer} for publishing log events onto the ring buffer.
 *
 * @author ofz
 */
public class DisruptorConfig {

    private static final Logger log = LoggerFactory.getLogger(DisruptorConfig.class);

    private final Disruptor<LogEvent> disruptor;
    private final LogEventProducer producer;

    public DisruptorConfig(int ringBufferSize,
                           ProducerType producerType,
                           WaitStrategy waitStrategy,
                           String threadNamePrefix,
                           LogFormatter formatter) {
        EventFactory<LogEvent> eventFactory = new LogEventFactory();
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r);
            t.setName(threadNamePrefix + t.getId());
            t.setDaemon(true);
            return t;
        };

        this.disruptor = new Disruptor<>(
                eventFactory,
                ringBufferSize,
                threadFactory,
                producerType,
                waitStrategy
        );

        // Single handler — ordered log consumption
        LogEventHandler handler = new LogEventHandler(formatter);
        disruptor.handleEventsWith(handler);

        disruptor.start();
        log.info("AutoLog Disruptor started — buffer size: {}, producer type: {}, wait strategy: {}",
                ringBufferSize, producerType, waitStrategy.getClass().getSimpleName());

        this.producer = new LogEventProducer(disruptor.getRingBuffer(),
                producerType == ProducerType.SINGLE);
    }

    /**
     * Returns the log event producer for publishing events to the ring buffer.
     */
    public LogEventProducer getProducer() {
        return producer;
    }

    /**
     * Gracefully shuts down the Disruptor, waiting for pending events to be processed.
     */
    public void shutdown() {
        log.info("Shutting down AutoLog Disruptor...");
        disruptor.shutdown();
        log.info("AutoLog Disruptor shut down complete.");
    }

    /**
     * Immediately halts the Disruptor without processing remaining events.
     */
    public void halt() {
        disruptor.halt();
    }

    /**
     * Parses a wait strategy name string into a {@link WaitStrategy} instance.
     */
    public static WaitStrategy parseWaitStrategy(String name) {
        if (name == null) {
            return new BlockingWaitStrategy();
        }
        String upper = name.trim().toUpperCase();
        switch (upper) {
            case "BLOCKING":
                return new BlockingWaitStrategy();
            case "SLEEPING":
                return new SleepingWaitStrategy();
            case "YIELDING":
                return new YieldingWaitStrategy();
            case "BUSY_SPIN":
                return new BusySpinWaitStrategy();
            default:
                log.warn("Unknown wait strategy '{}', falling back to BLOCKING", name);
                return new BlockingWaitStrategy();
        }
    }

    /**
     * Parses a producer type name string into a {@link ProducerType}.
     */
    public static ProducerType parseProducerType(String name) {
        if (name == null) {
            return ProducerType.MULTI;
        }
        String upper = name.trim().toUpperCase();
        switch (upper) {
            case "SINGLE":
                return ProducerType.SINGLE;
            case "MULTI":
                return ProducerType.MULTI;
            default:
                log.warn("Unknown producer type '{}', falling back to MULTI", name);
                return ProducerType.MULTI;
        }
    }

    /**
     * Returns the next power of 2 greater than or equal to the input,
     * clamped to the range [64, 65536].
     */
    public static int normalizeRingBufferSize(int size) {
        if (size < 64) {
            return 64;
        }
        if (size > 65536) {
            return 65536;
        }
        // Round up to next power of 2
        int n = size - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return n + 1;
    }
}
