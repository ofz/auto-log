package io.github.ofz.autolog.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Factory that pre-allocates {@link LogEvent} instances for the Disruptor ring buffer.
 *
 * @author ofz
 */
public class LogEventFactory implements EventFactory<LogEvent> {

    @Override
    public LogEvent newInstance() {
        return new LogEvent();
    }
}
