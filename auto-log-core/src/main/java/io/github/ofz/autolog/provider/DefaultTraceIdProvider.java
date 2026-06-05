package io.github.ofz.autolog.provider;

import org.slf4j.MDC;

/**
 * Default implementation of {@link TraceIdProvider} that resolves the trace ID
 * from SLF4J {@link MDC} under the key {@code "traceId"}.
 *
 * <h3>Integration with distributed tracing</h3>
 * Most tracing frameworks (Spring Cloud Sleuth, SkyWalking, OpenTelemetry
 * agent) populate MDC automatically. This provider reads the {@code traceId}
 * key from MDC, enabling zero-config integration.
 *
 * <h3>Manual trace ID control</h3>
 * <pre>{@code
 * MDC.put("traceId", traceId);
 * try {
 *     // request processing
 * } finally {
 *     MDC.remove("traceId");
 * }
 * }</pre>
 *
 * <h3>Custom implementation</h3>
 * Register a custom {@link TraceIdProvider} bean:
 * <pre>{@code
 * @Component
 * public class SkyWalkingTraceIdProvider implements TraceIdProvider {
 *     public String getTraceId() {
 *         return TraceContext.traceId();
 *     }
 * }
 * }</pre>
 *
 * @author ofz
 */
public class DefaultTraceIdProvider implements TraceIdProvider {

    /** MDC key used to look up the trace ID. */
    public static final String MDC_KEY = "traceId";

    /**
     * Reads the trace ID from {@link MDC} under {@link #MDC_KEY}.
     *
     * @return the trace ID, or {@code null} if not present
     */
    @Override
    public String getTraceId() {
        String traceId = MDC.get(MDC_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        return null;
    }
}
