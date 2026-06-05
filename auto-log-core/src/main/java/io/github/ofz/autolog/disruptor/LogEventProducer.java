package io.github.ofz.autolog.disruptor;

import com.lmax.disruptor.RingBuffer;
import io.github.ofz.autolog.context.LogContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer that publishes {@link LogContext} instances onto the Disruptor ring buffer.
 * Uses non-blocking {@code tryPublishEvent} to avoid stalling application threads
 * when the ring buffer is full.
 *
 * @author ofz
 */
public class LogEventProducer {

    private static final Logger log = LoggerFactory.getLogger(LogEventProducer.class);

    private final RingBuffer<LogEvent> ringBuffer;
    private final boolean blocking;

    /**
     * @param ringBuffer the Disruptor ring buffer to publish to
     * @param blocking   if {@code true}, use blocking {@code publishEvent};
     *                   if {@code false}, use non-blocking {@code tryPublishEvent}
     */
    public LogEventProducer(RingBuffer<LogEvent> ringBuffer, boolean blocking) {
        this.ringBuffer = ringBuffer;
        this.blocking = blocking;
    }

    /**
     * Publishes a LogContext to the ring buffer.
     *
     * @param context the log context to publish
     * @return {@code true} if the event was successfully published;
     *         {@code false} if the ring buffer is full (non-blocking mode only)
     */
    public boolean publish(LogContext context) {
        if (blocking) {
            ringBuffer.publishEvent((event, sequence, ctx) -> event.setLogContext(ctx), context);
            return true;
        }
        boolean success = ringBuffer.tryPublishEvent((event, sequence, ctx) -> event.setLogContext(ctx), context);
        if (!success) {
            log.warn("AutoLog ring buffer is full, dropping log event: {}", context);
        }
        return success;
    }
}
