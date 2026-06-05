package io.github.ofz.autolog.formatter;

import io.github.ofz.autolog.context.LogContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Default {@link LogFormatter} implementation.
 *
 * <p>Output format:
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

    @Override
    public String format(LogContext context) {
        String template = resolveTemplate(context);

        String message = template
                .replace("{class}", context.getClassName())
                .replace("{method}", context.getMethodName())
                .replace("{status}", context.getStatus() != null ? context.getStatus().name() : "UNKNOWN")
                .replace("{time}", String.valueOf(context.getExecutionTimeMs()))
                .replace("{operator}", notEmpty(context.getOperator()) ? context.getOperator() : "-")
                .replace("{traceId}", notEmpty(context.getTraceId()) ? context.getTraceId() : "-");

        // Replace {args} if placeholder is present
        if (message.contains("{args}")) {
            String argsStr = context.isLogArgs() ? formatArgs(context) : "[filtered]";
            message = message.replace("{args}", argsStr);
        }

        // Replace {result} if placeholder is present
        if (message.contains("{result}")) {
            String resultStr = context.isLogResult() ? formatResult(context.getResult()) : "[filtered]";
            message = message.replace("{result}", resultStr);
        }

        return message;
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

    /**
     * Formats method arguments as {@code paramName=value} pairs for log output.
     * <p>Uses {@link Method#getParameters()} to resolve parameter names (requires
     * the {@code -parameters} compiler flag, which is enabled by default in this
     * project). Falls back to {@code arg0, arg1} naming if names are unavailable.
     * <p>Arguments whose type is assignable to any type in
     * {@link LogContext#getExcludeTypes()} are silently skipped.
     * <p>Override for custom serialization.
     */
    protected String formatArgs(LogContext context) {
        Object[] args = context.getArgs();
        if (args == null || args.length == 0) {
            return "[]";
        }

        String[] paramNames = resolveParameterNames(context.getMethod());
        Class<?>[] excludeTypes = context.getExcludeTypes();

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < args.length; i++) {
            // Skip arguments whose type is in the exclude list
            if (isExcluded(args[i], excludeTypes)) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            String paramName = i < paramNames.length ? paramNames[i] : "arg" + i;
            sb.append(paramName).append("=").append(args[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Resolves parameter names from the method's {@link Parameter} metadata.
     * Falls back to synthetic names if the {@code -parameters} flag was not used.
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

    /**
     * Returns {@code true} if the string is non-null and non-empty.
     */
    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    /**
     * Formats the method return value for log output. Override for custom serialization.
     */
    protected String formatResult(Object result) {
        if (result == null) {
            return "null";
        }
        // Truncate long string results
        String s = String.valueOf(result);
        if (s.length() > 500) {
            return s.substring(0, 500) + "...";
        }
        return s;
    }
}
