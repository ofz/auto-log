package io.github.ofz.autolog.context;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all contextual information for a single auto-logged method invocation.
 * This is the data transfer object that flows through the Disruptor ring buffer.
 *
 * @author ofz
 */
public class LogContext {

    /** Fully qualified class name where the annotated method resides. */
    private final String className;

    /** Method name. */
    private final String methodName;

    /** The java.lang.reflect.Method object. */
    private final Method method;

    /** Method arguments as an array (may be empty). */
    private final Object[] args;

    /** Whether to log method arguments. */
    private final boolean logArgs;

    /** Whether to log return value. */
    private final boolean logResult;

    /** Whether to log execution time. */
    private final boolean logTime;

    /** Whether to log exception details. */
    private final boolean logException;

    /** Log level string (TRACE/DEBUG/INFO/WARN/ERROR). */
    private final String level;

    /** Custom message template from @AutoLog value(). */
    private final String messageTemplate;

    /** Execution start timestamp. */
    private Instant startTime;

    /** Execution end timestamp. */
    private Instant endTime;

    /** Method return value (null for void methods or exceptions). */
    private Object result;

    /** Exception thrown during execution, if any. */
    private Throwable exception;

    /** Execution status. */
    private ExecutionStatus status;

    /** Argument types to exclude from logging (from @AutoLog.excludeTypes). */
    private final Class<?>[] excludeTypes;

    /** Current operator (user) identifier, resolved via {@code OperatorProvider}. */
    private final String operator;

    /** Trace ID for distributed tracing, resolved via {@code TraceIdProvider}. */
    private final String traceId;

    /** Extra attributes for custom formatters. */
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    /**
     * Creates a new LogContext with pre-execution data.
     */
    public LogContext(String className, String methodName, Method method, Object[] args,
                      boolean logArgs, boolean logResult, boolean logTime,
                      boolean logException, String level, String messageTemplate,
                      Class<?>[] excludeTypes, String operator, String traceId) {
        this.className = className;
        this.methodName = methodName;
        this.method = method;
        this.args = args != null ? args.clone() : new Object[0];
        this.logArgs = logArgs;
        this.logResult = logResult;
        this.logTime = logTime;
        this.logException = logException;
        this.level = level;
        this.messageTemplate = messageTemplate;
        this.excludeTypes = excludeTypes != null ? excludeTypes.clone() : new Class<?>[0];
        this.operator = operator != null ? operator : "system";
        this.traceId = traceId != null ? traceId : "";
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
    }

    /**
     * Marks the execution as successfully completed.
     */
    public void markSuccess(Object result) {
        this.status = ExecutionStatus.SUCCESS;
        this.result = result;
        this.endTime = Instant.now();
    }

    /**
     * Marks the execution as failed due to an exception.
     */
    public void markFailure(Throwable exception) {
        this.status = ExecutionStatus.FAILURE;
        this.exception = exception;
        this.endTime = Instant.now();
    }

    /**
     * Returns the elapsed execution time in milliseconds.
     */
    public long getExecutionTimeMs() {
        if (startTime == null || endTime == null) {
            return -1;
        }
        return Duration.between(startTime, endTime).toMillis();
    }

    /**
     * Adds an extra attribute for custom formatting.
     */
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * Returns an unmodifiable view of extra attributes.
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // ---- Getters ----

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public boolean isLogArgs() {
        return logArgs;
    }

    public boolean isLogResult() {
        return logResult;
    }

    public boolean isLogTime() {
        return logTime;
    }

    public boolean isLogException() {
        return logException;
    }

    public Class<?>[] getExcludeTypes() {
        return excludeTypes;
    }

    public String getLevel() {
        return level;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Object getResult() {
        return result;
    }

    public Throwable getException() {
        return exception;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getOperator() {
        return operator;
    }

    public String getTraceId() {
        return traceId;
    }

    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        RUNNING,
        SUCCESS,
        FAILURE
    }

    @Override
    public String toString() {
        return "LogContext{" +
                "class='" + className + '\'' +
                ", method='" + methodName + '\'' +
                ", status=" + status +
                ", time=" + getExecutionTimeMs() + "ms" +
                ", exception=" + (exception != null ? exception.getClass().getSimpleName() : "null") +
                '}';
    }
}
