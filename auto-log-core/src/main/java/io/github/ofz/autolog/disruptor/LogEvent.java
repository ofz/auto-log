package io.github.ofz.autolog.disruptor;

import io.github.ofz.autolog.context.LogContext;

/**
 * Ring buffer event that carries a {@link LogContext} through the Disruptor pipeline.
 * Instances are pre-allocated and reused by the Disruptor for zero-GC event processing.
 *
 * @author ofz
 */
public class LogEvent {

    private LogContext logContext;

    public LogContext getLogContext() {
        return logContext;
    }

    public void setLogContext(LogContext logContext) {
        this.logContext = logContext;
    }

    /**
     * Clears the event after it has been consumed to allow GC of the payload
     * and avoid stale data in reused slots.
     */
    public void clear() {
        this.logContext = null;
    }
}
