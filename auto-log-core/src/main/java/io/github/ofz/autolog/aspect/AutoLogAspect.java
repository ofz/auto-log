package io.github.ofz.autolog.aspect;

import io.github.ofz.autolog.annotation.AutoLog;
import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.context.LogContextPool;
import io.github.ofz.autolog.disruptor.LogEventProducer;
import io.github.ofz.autolog.formatter.DefaultLogFormatter;
import io.github.ofz.autolog.formatter.LogFormatter;
import io.github.ofz.autolog.provider.AttributeProvider;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
 * <p>{@link LogContext} instances are borrowed from a {@link LogContextPool} and
 * returned after consumption to reduce allocation pressure and Young GC frequency.
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
    private final LogContextPool pool;
    private final List<AttributeProvider> attributeProviders;

    /**
     * Creates an aspect with all dependencies.
     */
    public AutoLogAspect(LogEventProducer producer, LogFormatter formatter,
                         OperatorProvider operatorProvider, TraceIdProvider traceIdProvider,
                         LogContextPool pool, List<AttributeProvider> attributeProviders) {
        this.producer = producer;
        this.formatter = formatter;
        this.operatorProvider = operatorProvider;
        this.traceIdProvider = traceIdProvider;
        this.pool = pool;
        this.attributeProviders = attributeProviders != null ? attributeProviders : Collections.emptyList();
    }

    /**
     * Creates an aspect with a producer, using defaults for everything else.
     */
    public AutoLogAspect(LogEventProducer producer) {
        this(producer, new DefaultLogFormatter(), new DefaultOperatorProvider(),
                new DefaultTraceIdProvider(), new LogContextPool(1024), Collections.emptyList());
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
     * class- and method-level), collects execution data, borrows a
     * {@link LogContext} from the pool, and publishes the log event asynchronously.
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

        LogContext ctx = pool.borrow();
        fillLogContext(ctx, targetClass, method, joinPoint.getArgs(),
                methodAnn, classAnn, operator, traceId);

        // Collect extra attributes from all registered AttributeProviders
        if (!attributeProviders.isEmpty()) {
            for (AttributeProvider provider : attributeProviders) {
                Map<String, Object> attrs = provider.getAttributes();
                if (attrs != null && !attrs.isEmpty()) {
                    for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                        ctx.addAttribute(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

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
     * Fills a LogContext (borrowed from the pool) by merging class-level and method-level
     * {@link AutoLog} annotation values. Method-level wins when both are present,
     * with the exception of {@code value()} and {@code excludeTypes()} where
     * an empty/zero-length method value falls back to the class-level one.
     *
     * @param ctx the LogContext to fill (already borrowed from the pool)
     */
    public static void fillLogContext(LogContext ctx, Class<?> targetClass, Method method, Object[] args,
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

        ctx.reset(
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
     * Builds a new {@link LogContext} (without pooling) by merging class-level and
     * method-level {@link AutoLog} annotation values.
     *
     * <p><strong>Note:</strong> This method allocates a new LogContext on every call.
     * For production use, the pooled path via {@link #fillLogContext} and
     * {@link LogContextPool#borrow()} is preferred. This method is retained for
     * backward compatibility and testing.
     */
    public static LogContext buildLogContext(Class<?> targetClass, Method method, Object[] args,
                                             AutoLog methodAnn, AutoLog classAnn,
                                             String operator, String traceId) {
        LogContext ctx = new LogContext();
        fillLogContext(ctx, targetClass, method, args, methodAnn, classAnn, operator, traceId);
        return ctx;
    }

    /**
     * Publishes the LogContext to the Disruptor, falling back to synchronous logging
     * if the producer is unavailable or the ring buffer is full.
     * On the synchronous fallback path, the context is returned to the pool here;
     * on the successful async path, the consumer thread returns it to the pool.
     */
    private void publish(LogContext ctx) {
        if (producer == null) {
            logSynchronously(ctx);
            pool.release(ctx);
            return;
        }
        try {
            boolean published = producer.publish(ctx);
            if (!published) {
                logSynchronously(ctx);
                pool.release(ctx);
            }
            // If published successfully, the consumer thread will release ctx to pool
        } catch (Exception e) {
            log.warn("Failed to publish AutoLog event: {}", e.getMessage());
            logSynchronously(ctx);
            pool.release(ctx);
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
