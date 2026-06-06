package io.github.ofz.autolog.formatter;

import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.provider.DefaultSensitiveDataFilter;
import io.github.ofz.autolog.provider.SensitiveDataFilter;

import org.springframework.expression.spel.support.StandardEvaluationContext;

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

    private final SensitiveDataFilter sensitiveFilter;
    private final TemplateCompiler compiler = new TemplateCompiler();
    private final int maxArgLength;
    private final int maxResultLength;

    /**
     * Creates a formatter with defaults (maxArgLength=200, maxResultLength=500).
     */
    public DefaultLogFormatter() {
        this(new DefaultSensitiveDataFilter());
    }

    /**
     * Creates a formatter with the given filter and default truncation lengths.
     */
    public DefaultLogFormatter(SensitiveDataFilter sensitiveFilter) {
        this(sensitiveFilter, 200, 500);
    }

    /**
     * Creates a formatter with full configuration.
     *
     * @param sensitiveFilter the sensitive-data filter
     * @param maxArgLength    max length of a single argument value (characters)
     * @param maxResultLength max length of the return value (characters)
     */
    public DefaultLogFormatter(SensitiveDataFilter sensitiveFilter, int maxArgLength, int maxResultLength) {
        this.sensitiveFilter = sensitiveFilter != null ? sensitiveFilter : new DefaultSensitiveDataFilter();
        this.maxArgLength = maxArgLength > 0 ? maxArgLength : 200;
        this.maxResultLength = maxResultLength > 0 ? maxResultLength : 500;
    }

    @Override
    public String format(LogContext context) {
        String template = resolveTemplate(context);
        CompiledTemplate compiled = compiler.compile(template);
        StringBuilder sb = new StringBuilder(template.length() + 256);

        StandardEvaluationContext spelCtx = null;
        if (compiled.hasSpel) {
            spelCtx = buildSpelContext(context);
        }
        for (TemplateSegment seg : compiled.segments) {
            switch (seg.type) {
                case LITERAL:
                    sb.append(seg.text);
                    break;
                case PLACEHOLDER:
                    appendPlaceholder(sb, seg.text, context);
                    break;
                case SPEL:
                    try {
                        Object val = seg.spelExpr.getValue(spelCtx);
                        appendSafeString(sb, val, maxArgLength);
                    } catch (Exception e) {
                        sb.append("[spel error: ").append(e.getClass().getSimpleName()).append(']');
                    }
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Builds a SpEL evaluation context populated with method parameters
     * (keyed by name), the return value ({@code #result}), and the
     * exception ({@code #exception}). Only called when the template
     * contains at least one {@code #{...}} expression.
     */
    private StandardEvaluationContext buildSpelContext(LogContext context) {
        StandardEvaluationContext evalCtx = new StandardEvaluationContext();
        Object[] args = context.getArgs();
        Method method = context.getMethod();
        if (args != null && method != null) {
            String[] paramNames = resolveParameterNames(method);
            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                evalCtx.setVariable(paramNames[i], args[i]);
            }
        }
        evalCtx.setVariable("result", context.getResult());
        evalCtx.setVariable("exception", context.getException());
        return evalCtx;
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
            case "slow":
                sb.append(context.isSlow() ? "SLOW" : "");
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
                appendSafeString(sb, args[i], maxArgLength);
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
        appendSafeString(sb, result, maxResultLength);
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
