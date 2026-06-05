package io.github.ofz.autolog.aspect;

import io.github.ofz.autolog.annotation.AutoLog;
import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.disruptor.LogEventProducer;
import io.github.ofz.autolog.formatter.DefaultLogFormatter;
import io.github.ofz.autolog.formatter.LogFormatter;
import io.github.ofz.autolog.provider.DefaultOperatorProvider;
import io.github.ofz.autolog.provider.DefaultTraceIdProvider;
import io.github.ofz.autolog.provider.OperatorProvider;
import io.github.ofz.autolog.provider.TraceIdProvider;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * AOP aspect that intercepts methods annotated with {@link AutoLog} at either the
 * class level or method level. When both levels carry the annotation, method-level
 * values take precedence (see {@link AutoLog} for merge rules).
 *
 * <p>Collects execution context (args, result, exception, timing, operator, traceId),
 * and publishes the {@link LogContext} to the Disruptor ring buffer for asynchronous
 * log output.
 *
 * <p>If the Disruptor producer is unavailable or publishing fails (buffer full in
 * non-blocking mode), logs are written synchronously as a fallback.
 *
 * @author ofz
 */
@Aspect
public class AutoLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AutoLogAspect.class);

    private final LogEventProducer producer;
    private final LogFormatter formatter;
    private final OperatorProvider operatorProvider;
    private final TraceIdProvider traceIdProvider;

    /**
     * Creates an aspect with a producer, formatter, and providers.
     */
    public AutoLogAspect(LogEventProducer producer, LogFormatter formatter,
                         OperatorProvider operatorProvider, TraceIdProvider traceIdProvider) {
        this.producer = producer;
        this.formatter = formatter;
        this.operatorProvider = operatorProvider;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * Creates an aspect with a producer, using defaults for formatter and providers.
     */
    public AutoLogAspect(LogEventProducer producer) {
        this(producer, new DefaultLogFormatter(), new DefaultOperatorProvider(), new DefaultTraceIdProvider());
    }

    @Pointcut("@annotation(io.github.ofz.autolog.annotation.AutoLog)")
    public void methodAutoLog() {
    }

    @Pointcut("@within(io.github.ofz.autolog.annotation.AutoLog)")
    public void classAutoLog() {
    }

    @Pointcut("methodAutoLog() || classAutoLog()")
    public void autoLogPointcut() {
    }

    /**
     * Around advice: resolves the effective {@link AutoLog} config (merging
     * class- and method-level), collects execution data, and publishes
     * the log event asynchronously.
     */
    @Around("autoLogPointcut()")
    public Object aroundAutoLog(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget() != null
                ? joinPoint.getTarget().getClass()
                : signature.getDeclaringType();

        AutoLog methodAnn = method.getAnnotation(AutoLog.class);
        AutoLog classAnn = targetClass.getAnnotation(AutoLog.class);

        String operator = operatorProvider != null ? operatorProvider.getOperator() : "system";
        String traceId = traceIdProvider != null ? traceIdProvider.getTraceId() : "";

        LogContext ctx = buildLogContext(targetClass, method, joinPoint.getArgs(),
                methodAnn, classAnn, operator, traceId);

        try {
            Object result = joinPoint.proceed();
            ctx.markSuccess(result);
            return result;
        } catch (Throwable t) {
            ctx.markFailure(t);
            throw t;
        } finally {
            publish(ctx);
        }
    }

    /**
     * Builds a {@link LogContext} by merging class-level and method-level
     * {@link AutoLog} annotation values. Method-level wins when both are present,
     * with the exception of {@code value()} and {@code excludeTypes()} where
     * an empty/zero-length method value falls back to the class-level one.
     */
    public static LogContext buildLogContext(Class<?> targetClass, Method method, Object[] args,
                                            AutoLog methodAnn, AutoLog classAnn,
                                            String operator, String traceId) {

        // value: method non-empty > class non-empty > ""
        String value;
        if (methodAnn != null && !methodAnn.value().isEmpty()) {
            value = methodAnn.value();
        } else if (classAnn != null && !classAnn.value().isEmpty()) {
            value = classAnn.value();
        } else {
            value = "";
        }

        // excludeTypes: method non-empty > class non-empty > {}
        Class<?>[] excludeTypes;
        if (methodAnn != null && methodAnn.excludeTypes().length > 0) {
            excludeTypes = methodAnn.excludeTypes();
        } else if (classAnn != null && classAnn.excludeTypes().length > 0) {
            excludeTypes = classAnn.excludeTypes();
        } else {
            excludeTypes = new Class<?>[0];
        }

        // Booleans and level: method wins, then class, then defaults
        boolean logArgs = methodAnn != null ? methodAnn.logArgs()
                : (classAnn == null || classAnn.logArgs());
        boolean logResult = methodAnn != null ? methodAnn.logResult()
                : (classAnn == null || classAnn.logResult());
        boolean logTime = methodAnn != null ? methodAnn.logTime()
                : (classAnn == null || classAnn.logTime());
        boolean logException = methodAnn != null ? methodAnn.logException()
                : (classAnn == null || classAnn.logException());
        String level = methodAnn != null ? methodAnn.level()
                : (classAnn != null ? classAnn.level() : "INFO");

        return new LogContext(
                targetClass.getName(),
                method.getName(),
                method,
                args,
                logArgs,
                logResult,
                logTime,
                logException,
                level,
                value,
                excludeTypes,
                operator,
                traceId
        );
    }

    /**
     * Publishes the LogContext to the Disruptor, falling back to synchronous logging
     * if the producer is unavailable or the ring buffer is full.
     */
    private void publish(LogContext ctx) {
        if (producer == null) {
            logSynchronously(ctx);
            return;
        }
        try {
            boolean published = producer.publish(ctx);
            if (!published) {
                logSynchronously(ctx);
            }
        } catch (Exception e) {
            log.warn("Failed to publish AutoLog event: {}", e.getMessage());
            logSynchronously(ctx);
        }
    }

    /**
     * Fallback: writes the log message synchronously on the calling thread.
     */
    private void logSynchronously(LogContext ctx) {
        try {
            String message = formatter.format(ctx);
            Logger logger = LoggerFactory.getLogger(ctx.getClassName());
            switch (ctx.getLevel().toUpperCase()) {
                case "TRACE":
                    logger.trace(message);
                    break;
                case "DEBUG":
                    logger.debug(message);
                    break;
                case "WARN":
                    logger.warn(message);
                    break;
                case "ERROR":
                    logger.error(message);
                    break;
                default:
                    logger.info(message);
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to log AutoLog synchronously: {}", e.getMessage(), e);
        }
    }
}
