package io.github.ofz.autolog.formatter;

import io.github.ofz.autolog.context.LogContext;

/**
 * Strategy interface for formatting auto-log output messages.
 *
 * <p>Implementations can customize the log format and content by providing
 * a Spring bean of this type. The default implementation is
 * {@link DefaultLogFormatter}.
 *
 * @author ofz
 */
@FunctionalInterface
public interface LogFormatter {

    /**
     * Formats the given {@link LogContext} into a log message string.
     *
     * @param context the log context with pre- and post-execution data
     * @return the formatted log message
     */
    String format(LogContext context);
}
