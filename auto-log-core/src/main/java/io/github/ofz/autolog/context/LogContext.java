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
 * <p>Instances are pooled via {@link LogContextPool} to reduce allocation pressure
 * and lower Young GC frequency. After consumption, callers should return the
 * instance to the pool rather than dropping it for GC.
 *
 * @author ofz
 */
public class LogContext {

    /** Shared empty array to avoid allocation in {@link #clearReferences()} and {@link #reset()}. */
    private static final Object[] EMPTY_ARGS = new Object[0];

    /** Shared empty array for excludeTypes. */
    private static final Class<?>[] EMPTY_EXCLUDE_TYPES = new Class<?>[0];

    /** Fully qualified class name where the annotated method resides. */
    private String className;

    /** Method name. */
    private String methodName;

    /** The java.lang.reflect.Method object. */
    private Method method;

    /** Method arguments as an array (may be empty). */
    private Object[] args;

    /** Whether to log method arguments. */
    private boolean logArgs;

    /** Whether to log return value. */
    private boolean logResult;

    /** Whether to log execution time. */
    private boolean logTime;

    /** Whether to log exception details. */
    private boolean logException;

    /** Log level string (TRACE/DEBUG/INFO/WARN/ERROR). */
    private String level;

    /** Custom message template from @AutoLog value(). */
    private String messageTemplate;

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
    private Class<?>[] excludeTypes;

    /** Current operator (user) identifier, resolved via {@code OperatorProvider}. */
    private String operator;

    /** Trace ID for distributed tracing, resolved via {@code TraceIdProvider}. */
    private String traceId;

    /**
     * Extra attributes for custom formatters. Lazily initialised on first
     * call to {@link #addAttribute} — the vast majority of invocations
     * never use this, so we avoid allocating a Map per pooled instance.
     */
    private Map<String, Object> attributes;

    /**
     * No-arg constructor for pool pre-allocation.
     * Creates an empty context with safe defaults; call {@link #reset} before use.
     */
    public LogContext() {
        this.args = EMPTY_ARGS;
        this.excludeTypes = EMPTY_EXCLUDE_TYPES;
        this.operator = "system";
        this.traceId = "";
        this.level = "INFO";
        this.status = ExecutionStatus.RUNNING;
    }

    /**
     * Creates a new LogContext with pre-execution data.
     */
    public LogContext(String className, String methodName, Method method, Object[] args,
                      boolean logArgs, boolean logResult, boolean logTime,
                      boolean logException, String level, String messageTemplate,
                      Class<?>[] excludeTypes, String operator, String traceId) {
        reset(className, methodName, method, args,
                logArgs, logResult, logTime, logException,
                level, messageTemplate, excludeTypes, operator, traceId);
    }

    /**
     * Resets all fields for reuse from the object pool.
     * This avoids the allocation cost of creating a new LogContext for every invocation.
     */
    public void reset(String className, String methodName, Method method, Object[] args,
                      boolean logArgs, boolean logResult, boolean logTime,
                      boolean logException, String level, String messageTemplate,
                      Class<?>[] excludeTypes, String operator, String traceId) {
        this.className = className;
        this.methodName = methodName;
        this.method = method;
        this.args = args != null ? args.clone() : EMPTY_ARGS;
        this.logArgs = logArgs;
        this.logResult = logResult;
        this.logTime = logTime;
        this.logException = logException;
        this.level = level;
        this.messageTemplate = messageTemplate;
        this.excludeTypes = excludeTypes != null ? excludeTypes.clone() : EMPTY_EXCLUDE_TYPES;
        this.operator = operator != null ? operator : "system";
        this.traceId = traceId != null ? traceId : "";
        this.startTime = Instant.now();
        this.status = ExecutionStatus.RUNNING;
        this.endTime = null;
        this.result = null;
        this.exception = null;
        if (attributes != null) {
            attributes.clear();
        }
    }

    /**
     * Clears all reference-type fields to prevent memory leaks when a
     * LogContext is returned to the object pool. This ensures that
     * method arguments, results, exceptions, and other objects
     * referenced by this context are eligible for GC even while
     * this instance sits idle in the pool.
     */
    public void clearReferences() {
        this.className = null;
        this.methodName = null;
        this.method = null;
        this.args = EMPTY_ARGS;
        this.messageTemplate = null;
        this.startTime = null;
        this.endTime = null;
        this.result = null;
        this.exception = null;
        this.excludeTypes = EMPTY_EXCLUDE_TYPES;
        this.operator = "system";
        this.traceId = "";
        if (attributes != null) {
            attributes.clear();
        }
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
     * The underlying map is created lazily on first use.
     */
    public void addAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(key, value);
    }

    /**
     * Returns an unmodifiable view of extra attributes, or an empty map
     * if no attributes have been added (lazy-init, zero allocation).
     */
    public Map<String, Object> getAttributes() {
        if (attributes == null) {
            return Collections.emptyMap();
        }
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
