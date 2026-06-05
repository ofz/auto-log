package io.github.ofz.autolog.provider;

/**
 * Strategy interface for resolving the trace ID associated with a log entry.
 * Implement this interface and register a Spring bean to integrate with your
 * existing tracing infrastructure (e.g. OpenTelemetry, SkyWalking, custom MDC setup).
 *
 * <p>The default implementation uses a {@code ThreadLocal} to store trace IDs
 * and generates a UUID when none is set.
 *
 * @author ofz
 */
@FunctionalInterface
public interface TraceIdProvider {

    /**
     * Returns the current trace ID.
     * This method is called at log-collection time (on the application thread,
     * before the event is published to Disruptor).
     *
     * @return the trace ID string, never null
     */
    String getTraceId();
}
