package io.github.ofz.autolog.provider;

import java.lang.reflect.Method;

/**
 * Strategy interface for identifying method parameters that contain sensitive
 * data and should be masked in log output.
 *
 * <p>Implement this interface and register a Spring bean to customize which
 * parameters are considered sensitive. The default implementation
 * {@code DefaultSensitiveDataFilter} matches common keyword patterns
 * ({@code password}, {@code token}, {@code secret}, etc.) and can be
 * extended via the {@code auto.log.sensitive-keywords} configuration property.
 *
 * <p>When a parameter is flagged as sensitive, its value is replaced with
 * {@code ***} in the log output, but the parameter name is still shown.
 * This differs from {@link io.github.ofz.autolog.annotation.AutoLog#excludeTypes()}
 * which removes the parameter entirely from the log.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * @Component
 * public class CustomSensitiveDataFilter implements SensitiveDataFilter {
 *     public boolean isSensitive(String paramName, Object paramValue, Method method) {
 *         return paramName != null && paramName.toLowerCase().contains("ssn");
 *     }
 * }
 * }</pre>
 *
 * @author ofz
 */
@FunctionalInterface
public interface SensitiveDataFilter {

    /**
     * Determines whether the given method parameter should be masked.
     * Called during log formatting on the Disruptor consumer thread.
     *
     * @param paramName the parameter name resolved from reflection metadata
     * @param paramValue the actual argument value (may be null)
     * @param method the method being logged
     * @return {@code true} to mask the value as {@code ***}
     */
    boolean isSensitive(String paramName, Object paramValue, Method method);
}
