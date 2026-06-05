package io.github.ofz.autolog.disruptor;

import com.lmax.disruptor.EventHandler;
import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.formatter.LogFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Disruptor event handler that consumes {@link LogEvent} instances from the ring buffer
 * and performs the actual log output. Each event is formatted using the configured
 * {@link LogFormatter} and written via SLF4J at the log level specified in the annotation.
 *
 * <p>This handler runs on the Disruptor's dedicated thread(s), keeping logging I/O
 * off the application threads.
 *
 * @author ofz
 */
public class LogEventHandler implements EventHandler<LogEvent> {

    private final LogFormatter formatter;

    public LogEventHandler(LogFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) {
        LogContext ctx = event.getLogContext();
        if (ctx == null) {
            return;
        }
        try {
            String message = formatter.format(ctx);
            Logger logger = LoggerFactory.getLogger(ctx.getClassName());

            switch (ctx.getLevel().toUpperCase()) {
                case "TRACE":
                    if (logger.isTraceEnabled()) {
                        logger.trace(message);
                    }
                    break;
                case "DEBUG":
                    if (logger.isDebugEnabled()) {
                        logger.debug(message);
                    }
                    break;
                case "WARN":
                    if (logger.isWarnEnabled()) {
                        logger.warn(message);
                    }
                    break;
                case "ERROR":
                    if (logger.isErrorEnabled()) {
                        logger.error(message);
                    }
                    break;
                default:
                    if (logger.isInfoEnabled()) {
                        logger.info(message);
                    }
                    break;
            }
        } catch (Exception e) {
            // Logging should never throw — silently swallow to avoid disrupting the ring buffer
            LoggerFactory.getLogger(LogEventHandler.class)
                    .error("Failed to process AutoLog event: {}", e.getMessage(), e);
        } finally {
            event.clear();
        }
    }
}
