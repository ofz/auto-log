package io.github.ofz.autolog.provider;

import java.util.Map;

/**
 * Strategy interface for providing extra key-value attributes that are
 * attached to every {@code LogContext} and made available to custom
 * {@link io.github.ofz.autolog.formatter.LogFormatter} implementations
 * via {@link io.github.ofz.autolog.context.LogContext#getAttributes()}.
 *
 * <p>Implement this interface and register a Spring bean to inject
 * custom metadata (e.g. userId, requestId, tenantId, feature flags)
 * into every auto-log entry without subclassing the AOP aspect.
 *
 * <p>Multiple {@code AttributeProvider} beans may be registered — all
 * of them are called for every invocation. If two providers write the
 * same key the last one wins (insertion order follows the Spring bean
 * ordering).
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @Component
 * public class UserAttributeProvider implements AttributeProvider {
 *     public Map<String, Object> getAttributes() {
 *         Map<String, Object> attrs = new HashMap<>();
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth != null) {
 *             attrs.put("userId", auth.getName());
 *             attrs.put("roles", auth.getAuthorities());
 *         }
 *         return attrs;
 *     }
 * }
 * }</pre>
 *
 * @author ofz
 */
@FunctionalInterface
public interface AttributeProvider {

    /**
     * Returns extra attributes for the current invocation.
     * This method is called at log-collection time on the application thread,
     * before the event is published to the Disruptor.
     *
     * <p>Return an empty map (never {@code null}) when there are no
     * attributes to contribute — this avoids unnecessary iteration and
     * map allocation inside the aspect.
     *
     * @return a map of attribute key-value pairs, never null
     */
    Map<String, Object> getAttributes();
}
