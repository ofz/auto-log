package io.github.ofz.autolog.formatter;

import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.provider.DefaultSensitiveDataFilter;
import io.github.ofz.autolog.provider.SensitiveDataFilter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * Default {@link LogFormatter} implementation.
 *
 * <p>Uses a zero-copy {@link StringBuilder}-based template engine that avoids the
 * intermediate string allocations of chained {@code String.replace()} calls.
 *
 * <p>Integrates with {@link SensitiveDataFilter} to mask sensitive parameter
 * values (e.g. passwords, tokens) in log output. Sensitive values are shown as
 * {@code ***} while the parameter name is preserved.
 *
 * <p>Output format (default template):
 * <pre>[AutoLog] {operator} | {traceId} | {class}#{method} | {status} | {time}ms | args={args} | result={result}</pre>
 *
 * <p>Available placeholders: {@code {class}}, {@code {method}}, {@code {args}},
 * {@code {result}}, {@code {time}}, {@code {status}}, {@code {operator}}, {@code {traceId}}.
 *
 * <p>Register a custom {@link LogFormatter} bean to override this behavior entirely.
 *
 * @author ofz
 */
public class DefaultLogFormatter implements LogFormatter {

    private static final String DEFAULT_TEMPLATE =
            "[AutoLog] {operator} | {traceId} | {class}#{method} | {status} | {time}ms | args={args} | result={result}";

    private static final String MASKED_VALUE = "***";

    /** Maximum length of a single argument's {@code toString()} output before truncation. */
    private static final int MAX_ARG_LENGTH = 200;

    /** Maximum length of the result's {@code toString()} output before truncation. */
    private static final int MAX_RESULT_LENGTH = 500;

    private final SensitiveDataFilter sensitiveFilter;

    /**
     * Creates a formatter with the default {@link SensitiveDataFilter}.
     */
    public DefaultLogFormatter() {
        this(new DefaultSensitiveDataFilter());
    }

    /**
     * Creates a formatter with the given {@link SensitiveDataFilter}.
     *
     * @param sensitiveFilter the filter to use for masking sensitive parameter values
     */
    public DefaultLogFormatter(SensitiveDataFilter sensitiveFilter) {
        this.sensitiveFilter = sensitiveFilter != null ? sensitiveFilter : new DefaultSensitiveDataFilter();
    }

    @Override
    public String format(LogContext context) {
        String template = resolveTemplate(context);
        StringBuilder sb = new StringBuilder(template.length() + 256);
        renderTemplate(sb, template, context);
        return sb.toString();
    }

    /**
     * Renders the template into the given {@link StringBuilder} by scanning
     * for {@code {placeholder}} tokens and resolving each one against the
     * {@link LogContext}. Literal text between placeholders is appended
     * directly — no intermediate strings are created.
     */
    private void renderTemplate(StringBuilder sb, String template, LogContext context) {
        int pos = 0;
        int len = template.length();
        while (pos < len) {
            int brace = template.indexOf('{', pos);
            if (brace < 0) {
                sb.append(template, pos, len);
                return;
            }
            // Append literal text before the brace
            sb.append(template, pos, brace);
            int endBrace = template.indexOf('}', brace + 1);
            if (endBrace < 0) {
                // Malformed — no closing brace; append the rest as-is
                sb.append(template, brace, len - brace);
                return;
            }
            // Resolve placeholder
            String key = template.substring(brace + 1, endBrace);
            appendPlaceholder(sb, key, context);
            pos = endBrace + 1;
        }
    }

    /**
     * Resolves a single placeholder and appends its value to the builder.
     * Unknown placeholders are rendered as-is ({@code {unknown}}).
     */
    private void appendPlaceholder(StringBuilder sb, String key, LogContext context) {
        switch (key) {
            case "class":
                sb.append(context.getClassName());
                break;
            case "method":
                sb.append(context.getMethodName());
                break;
            case "status":
                sb.append(context.getStatus() != null ? context.getStatus().name() : "UNKNOWN");
                break;
            case "time":
                sb.append(context.getExecutionTimeMs());
                break;
            case "operator":
                sb.append(notEmpty(context.getOperator()) ? context.getOperator() : "-");
                break;
            case "traceId":
                sb.append(notEmpty(context.getTraceId()) ? context.getTraceId() : "-");
                break;
            case "args":
                if (context.isLogArgs()) {
                    formatArgs(sb, context);
                } else {
                    sb.append("[filtered]");
                }
                break;
            case "result":
                if (context.isLogResult()) {
                    formatResult(sb, context.getResult());
                } else {
                    sb.append("[filtered]");
                }
                break;
            default:
                // Unknown placeholder — render as-is
                sb.append('{').append(key).append('}');
                break;
        }
    }

    // ---- Argument formatting ----

    /**
     * Formats method arguments into the given builder as
     * {@code [paramName=value, ...]}. Parameters whose type matches an
     * excluded type are skipped entirely. Parameters flagged as sensitive
     * by the {@link SensitiveDataFilter} show their name but have their
     * value replaced with {@code ***}.
     *
     * <p>Overriding note: subclasses that override this method should use
     * {@code StringBuilder} rather than string concatenation.
     */
    protected void formatArgs(StringBuilder sb, LogContext context) {
        Object[] args = context.getArgs();
        if (args == null || args.length == 0) {
            sb.append("[]");
            return;
        }

        String[] paramNames = resolveParameterNames(context.getMethod());
        Class<?>[] excludeTypes = context.getExcludeTypes();

        sb.append('[');
        boolean first = true;
        for (int i = 0; i < args.length; i++) {
            // Skip excluded types entirely
            if (isExcluded(args[i], excludeTypes)) {
                continue;
            }
            if (!first) {
                sb.append(", ");
            }
            String paramName = i < paramNames.length ? paramNames[i] : "arg" + i;
            sb.append(paramName).append('=');
            if (isSensitive(paramName, args[i], context)) {
                sb.append(MASKED_VALUE);
            } else {
                appendSafeString(sb, args[i], MAX_ARG_LENGTH);
            }
            first = false;
        }
        sb.append(']');
    }

    // ---- Result formatting ----

    /**
     * Formats the method return value into the given builder.
     * Long string results are truncated at 500 characters.
     *
     * <p>Overriding note: subclasses that override this method should use
     * {@code StringBuilder} rather than string concatenation.
     */
    protected void formatResult(StringBuilder sb, Object result) {
        appendSafeString(sb, result, MAX_RESULT_LENGTH);
    }

    /**
     * Checks whether a parameter value should be masked, combining the global
     * {@link SensitiveDataFilter} with the method-level {@code @AutoLog.sensitiveParams}.
     */
    private boolean isSensitive(String paramName, Object paramValue, LogContext context) {
        if (sensitiveFilter.isSensitive(paramName, paramValue, context.getMethod())) {
            return true;
        }
        String[] annotationParams = context.getSensitiveParams();
        if (annotationParams != null && annotationParams.length > 0) {
            for (String sp : annotationParams) {
                if (sp != null && sp.equals(paramName)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Safe toString ----

    /**
     * Converts an arbitrary object to a string representation and appends it
     * to the builder, with the following safety guarantees:
     * <ul>
     *   <li>{@code null} → {@code "null"}</li>
     *   <li>Arrays → meaningful representation via {@link Arrays#toString}
     *       (instead of the useless default {@code [Ljava.lang.Object;@hash})</li>
     *   <li>{@code toString()} exceptions are caught → {@code [toString error: ...]}</li>
     *   <li>Output is truncated at {@code maxLen} characters</li>
     * </ul>
     */
    private static void appendSafeString(StringBuilder sb, Object obj, int maxLen) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        String s;
        try {
            if (obj.getClass().isArray()) {
                s = arrayToString(obj);
            } else {
                s = obj.toString();
            }
        } catch (Exception e) {
            s = "[toString error: " + e.getClass().getSimpleName() + "]";
        }
        if (s.length() > maxLen) {
            sb.append(s, 0, maxLen).append("...");
        } else {
            sb.append(s);
        }
    }

    /**
     * Converts a primitive or object array to a readable string.
     * Large arrays ({@code byte[]}, {@code char[]}) only show their length
     * to avoid flooding the log.
     */
    private static String arrayToString(Object array) {
        if (array instanceof Object[]) return Arrays.toString((Object[]) array);
        if (array instanceof int[])     return Arrays.toString((int[]) array);
        if (array instanceof long[])    return Arrays.toString((long[]) array);
        if (array instanceof double[])  return Arrays.toString((double[]) array);
        if (array instanceof float[])   return Arrays.toString((float[]) array);
        if (array instanceof short[])   return Arrays.toString((short[]) array);
        if (array instanceof boolean[]) return Arrays.toString((boolean[]) array);
        if (array instanceof byte[])    return "[byte[" + ((byte[]) array).length + "]]";
        if (array instanceof char[])    return "[char[" + ((char[]) array).length + "]]";
        return "[array]";
    }

    /**
     * Determines which template string to use: the annotation's custom message,
     * or the default template.
     */
    protected String resolveTemplate(LogContext context) {
        String custom = context.getMessageTemplate();
        if (custom != null && !custom.isEmpty()) {
            return custom;
        }
        return DEFAULT_TEMPLATE;
    }

    // ---- Parameter name resolution ----

    /**
     * Resolves parameter names from the method's {@link Parameter} metadata.
     * Falls back to {@code arg0, arg1, ...} naming if names are unavailable
     * (requires the {@code -parameters} compiler flag).
     */
    protected String[] resolveParameterNames(Method method) {
        if (method == null) {
            return new String[0];
        }
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();
            // If synthetic (no -parameters flag), use positional name
            if (name == null || name.startsWith("arg")) {
                names[i] = "arg" + i;
            } else {
                names[i] = name;
            }
        }
        return names;
    }

    // ---- Helpers ----

    /**
     * Checks whether the given argument's type is assignable to any type
     * in the excluded types array.
     */
    protected boolean isExcluded(Object arg, Class<?>[] excludeTypes) {
        if (excludeTypes == null || excludeTypes.length == 0 || arg == null) {
            return false;
        }
        for (Class<?> excludedType : excludeTypes) {
            if (excludedType != null && excludedType.isInstance(arg)) {
                return true;
            }
        }
        return false;
    }

    /** Returns {@code true} if the string is non-null and non-empty. */
    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
