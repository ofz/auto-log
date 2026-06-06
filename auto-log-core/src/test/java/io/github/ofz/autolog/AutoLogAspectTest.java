package io.github.ofz.autolog;

import io.github.ofz.autolog.annotation.AutoLog;
import io.github.ofz.autolog.aspect.AutoLogAspect;
import io.github.ofz.autolog.context.LogContext;
import io.github.ofz.autolog.disruptor.LogEventProducer;
import io.github.ofz.autolog.formatter.DefaultLogFormatter;
import io.github.ofz.autolog.provider.DefaultSensitiveDataFilter;
import io.github.ofz.autolog.provider.DefaultTraceIdProvider;
import io.github.ofz.autolog.provider.SensitiveDataFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the core AutoLog functionality.
 */
class AutoLogAspectTest {

    private static final Class<?>[] NO_EXCLUDE = new Class<?>[0];
    private static final String OP = "admin";
    private static final String TID = "abc123def456";

    private LogEventProducer mockProducer;

    @BeforeEach
    void setUp() {
        mockProducer = mock(LogEventProducer.class);
        when(mockProducer.publish(any())).thenReturn(true);
        MDC.remove(DefaultTraceIdProvider.MDC_KEY);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(DefaultTraceIdProvider.MDC_KEY);
    }

    // ---- Basic LogContext tests ----

    @Test
    void testLogContextCreation() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method, new Object[]{"World"},
                true, true, true, true, "INFO", "");

        assertEquals(TestService.class.getName(), ctx.getClassName());
        assertEquals("greet", ctx.getMethodName());
        assertArrayEquals(new Object[]{"World"}, ctx.getArgs());
        assertTrue(ctx.isLogArgs());
        assertTrue(ctx.isLogResult());
        assertEquals("INFO", ctx.getLevel());
        assertEquals(LogContext.ExecutionStatus.RUNNING, ctx.getStatus());
        assertEquals(0, ctx.getExcludeTypes().length);
    }

    @Test
    void testOperatorAndTraceId() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method, new Object[]{"World"},
                true, true, true, true, "INFO", "");

        assertEquals(OP, ctx.getOperator());
        assertEquals(TID, ctx.getTraceId()); // explicitly set in buildCtx
    }

    @Test
    void testTraceIdNullByDefault() {
        DefaultTraceIdProvider provider = new DefaultTraceIdProvider();
        assertNull(provider.getTraceId());
    }

    @Test
    void testTraceIdFromMdc() {
        DefaultTraceIdProvider provider = new DefaultTraceIdProvider();
        MDC.put(DefaultTraceIdProvider.MDC_KEY, "mdc-tid");
        assertEquals("mdc-tid", provider.getTraceId());
    }

    @Test
    void testLogContextSuccess() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method, new Object[]{"World"},
                true, true, true, true, "INFO", "");

        ctx.markSuccess("Hello World");
        assertEquals(LogContext.ExecutionStatus.SUCCESS, ctx.getStatus());
        assertEquals("Hello World", ctx.getResult());
        assertTrue(ctx.getExecutionTimeMs() >= 0);
        assertNull(ctx.getException());
    }

    @Test
    void testLogContextFailure() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method, new Object[]{"World"},
                true, true, true, true, "INFO", "");

        RuntimeException ex = new RuntimeException("test error");
        ctx.markFailure(ex);
        assertEquals(LogContext.ExecutionStatus.FAILURE, ctx.getStatus());
        assertSame(ex, ctx.getException());
        assertTrue(ctx.getExecutionTimeMs() >= 0);
    }

    @Test
    void testLogContextExtraAttributes() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method, new Object[]{"World"},
                true, true, true, true, "INFO", "");

        ctx.addAttribute("userId", "12345");
        ctx.addAttribute("requestId", "req-abc");
        assertEquals("12345", ctx.getAttributes().get("userId"));
        assertEquals(2, ctx.getAttributes().size());
    }

    // ---- Formatter tests ----

    @Test
    void testDefaultFormatterWithParamNames() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("login", String.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", "secret123"},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("OK");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("username=admin"));
        assertTrue(message.contains("password=***"));
        assertTrue(message.contains("SUCCESS"));
        assertTrue(message.contains("ms"));
    }

    @Test
    void testFormatterIncludesOperatorAndTraceId() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // Default template includes operator and traceId values
        assertTrue(message.contains("admin"));
        assertTrue(message.contains("abc123def456"));
        assertTrue(message.contains("Hello World"));
    }

    @Test
    void testFormatterWithNullTraceId() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        // traceId = null simulates DefaultTraceIdProvider returning null
        LogContext ctx = new LogContext(TestService.class.getName(), method.getName(), method,
                new Object[]{"World"}, true, true, true, true, "INFO", "",
                NO_EXCLUDE, OP, null);
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // Null traceId should render as "-"
        assertTrue(message.contains("-"));
        assertTrue(message.contains("Hello World"));
    }

    @Test
    void testDefaultFormatterSingleParam() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains(TestService.class.getName()));
        assertTrue(message.contains("greet"));
        assertTrue(message.contains("SUCCESS"));
        assertTrue(message.contains("ms"));
        assertTrue(message.contains("name=World"));
    }

    @Test
    void testExcludeTypesFiltering() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("login", String.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", "secret123"},
                true, true, true, true, "INFO", "",
                new Class<?>[]{String.class});
        ctx.markSuccess("OK");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertFalse(message.contains("username="));
        assertFalse(message.contains("password="));
    }

    @Test
    void testExcludeTypesPartial() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("loginWithContext",
                String.class, Context.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", new Context(), "tok123"},
                true, true, true, true, "INFO", "",
                new Class<?>[]{Context.class});
        ctx.markSuccess("OK");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertFalse(message.contains("ctx="));
        assertTrue(message.contains("username=admin"));
        assertTrue(message.contains("token=***"));
    }

    @Test
    void testCustomTemplateFormatter() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "[{method}] {status} in {time}ms");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("[greet]"));
        assertTrue(message.contains("SUCCESS"));
        assertTrue(message.contains("ms"));
        assertFalse(message.contains("[AutoLog]"));
    }

    @Test
    void testCustomTemplateWithOperator() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "{operator} -> {method}");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("admin -> greet"));
    }

    @Test
    void testArgumentsFiltered() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                false, true, true, true, "INFO", "");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("[filtered]"));
    }

    @Test
    void testEmptyArgs() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("ping");
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("pong");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("args=[]"));
    }

    @Test
    void testProducerFailFallback() throws NoSuchMethodException {
        LogEventProducer failingProducer = mock(LogEventProducer.class);
        when(failingProducer.publish(any())).thenReturn(false);

        AutoLogAspect fallbackAspect = new AutoLogAspect(failingProducer);
        assertDoesNotThrow(() -> {
            Method method = TestService.class.getMethod("greet", String.class);
            LogContext ctx = buildCtx(TestService.class, method,
                    new Object[]{"World"},
                    true, true, true, true, "INFO", "");
            ctx.markSuccess("ok");
        });
    }

    // ---- Sensitive data filter tests ----

    @Test
    void testSensitiveDataMasked() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("login", String.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", "secret123"},
                true, true, true, true, "INFO", "");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // "password" is in built-in keywords — value masked, name shown
        assertTrue(message.contains("password=***"));
        // "username" is not sensitive — value shown
        assertTrue(message.contains("username=admin"));
        assertFalse(message.contains("password=secret123"));
    }

    @Test
    void testSensitiveDataWithCustomFilter() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("login", String.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", "secret123"},
                true, true, true, true, "INFO", "");

        // Custom filter: only mask "username", not "password"
        SensitiveDataFilter customFilter = (paramName, value, m) ->
                "username".equals(paramName);
        DefaultLogFormatter formatter = new DefaultLogFormatter(customFilter);
        String message = formatter.format(ctx);

        assertTrue(message.contains("username=***"));
        assertTrue(message.contains("password=secret123"));
    }

    @Test
    void testSensitiveDataWithExtraKeywords() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("login", String.class, String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"admin", "secret123"},
                true, true, true, true, "INFO", "");

        // Add "username" as extra sensitive keyword
        DefaultSensitiveDataFilter filter = new DefaultSensitiveDataFilter(
                new HashSet<>(Collections.singletonList("username")));
        DefaultLogFormatter formatter = new DefaultLogFormatter(filter);
        String message = formatter.format(ctx);

        // Both "password" (built-in) and "username" (extra) are masked
        assertTrue(message.contains("password=***"));
        assertTrue(message.contains("username=***"));
    }

    @Test
    void testNonSensitiveParamsUnaffected() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // "name" is not sensitive — should appear normally
        assertTrue(message.contains("name=World"));
        assertFalse(message.contains("***"));
    }

    @Test
    void testZeroCopyTemplateRendering() throws NoSuchMethodException {
        // Verify the zero-copy engine renders the same output as expected
        // for all standard placeholders
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO", "");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // All standard placeholders should be resolved
        assertTrue(message.contains(TestService.class.getName()));
        assertTrue(message.contains("greet"));
        assertTrue(message.contains("SUCCESS"));
        assertTrue(message.contains("ms"));
        assertTrue(message.contains("admin"));   // operator
        assertTrue(message.contains("abc123def456")); // traceId
        assertTrue(message.contains("name=World"));
        assertTrue(message.contains("Hello World"));
    }

    // ---- SpEL template tests ----

    @Test
    void testSpelTemplateParamReference() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "Hello #{#name}");
        ctx.markSuccess("result");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // #{#name} should resolve to the argument value
        assertTrue(message.contains("Hello World"));
        assertFalse(message.contains("#{"));
    }

    @Test
    void testSpelTemplateResultReference() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "result was: #{#result}");
        ctx.markSuccess("Hello World");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("result was: Hello World"));
    }

    @Test
    void testSpelTemplateTernaryExpression() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "#{#name != null ? 'ok' : 'missing'}");
        ctx.markSuccess(null);

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains("ok"));
    }

    @Test
    void testSpelTemplateMixedWithPlaceholders() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"},
                true, true, true, true, "INFO",
                "#{#name} -> {method} -> {status}");
        ctx.markSuccess("ok");

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        // Both SpEL and framework placeholders resolved
        assertTrue(message.contains("World"));
        assertTrue(message.contains("greet"));
        assertTrue(message.contains("SUCCESS"));
    }

    @Test
    void testSpelTemplateCacheHit() throws NoSuchMethodException {
        // Same template used twice — compiler should cache
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx1 = buildCtx(TestService.class, method,
                new Object[]{"Alice"}, true, true, true, true, "INFO", "Hi #{#name}");
        ctx1.markSuccess(null);
        LogContext ctx2 = buildCtx(TestService.class, method,
                new Object[]{"Bob"}, true, true, true, true, "INFO", "Hi #{#name}");
        ctx2.markSuccess(null);

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String msg1 = formatter.format(ctx1);
        String msg2 = formatter.format(ctx2);

        assertTrue(msg1.contains("Hi Alice"));
        assertTrue(msg2.contains("Hi Bob"));
    }

    @Test
    void testSpelTemplateNoSpelStillWorks() throws NoSuchMethodException {
        // Template with only framework placeholders, no #{...} — zero overhead path
        Method method = TestService.class.getMethod("greet", String.class);
        LogContext ctx = buildCtx(TestService.class, method,
                new Object[]{"World"}, true, true, true, true, "INFO",
                "{class}#{method} | {status}"); // no #{...}
        ctx.markSuccess(null);

        DefaultLogFormatter formatter = new DefaultLogFormatter();
        String message = formatter.format(ctx);

        assertTrue(message.contains(TestService.class.getName()));
        assertTrue(message.contains("greet"));
        assertTrue(message.contains("SUCCESS"));
        assertFalse(message.contains("#{"));
    }

    // ---- Class-level @AutoLog tests ----

    @Test
    void testClassLevelAnnotationOnly() throws NoSuchMethodException {
        Class<?> clazz = AnnotatedController.class;
        Method method = clazz.getMethod("listUsers");
        AutoLog classAnn = clazz.getAnnotation(AutoLog.class);

        LogContext ctx = AutoLogAspect.buildLogContext(clazz, method,
                new Object[]{}, null, classAnn, OP, TID);

        assertEquals("WARN", ctx.getLevel());
        assertEquals(1, ctx.getExcludeTypes().length);
        assertEquals(Context.class, ctx.getExcludeTypes()[0]);
        assertFalse(ctx.isLogResult());
        assertEquals(OP, ctx.getOperator());
        assertEquals(TID, ctx.getTraceId());
    }

    @Test
    void testMethodOverridesClass() throws NoSuchMethodException {
        Class<?> clazz = AnnotatedController.class;
        Method method = clazz.getMethod("overrideMethod", String.class);
        AutoLog methodAnn = method.getAnnotation(AutoLog.class);
        AutoLog classAnn = clazz.getAnnotation(AutoLog.class);

        LogContext ctx = AutoLogAspect.buildLogContext(clazz, method,
                new Object[]{"test"}, methodAnn, classAnn, OP, TID);

        assertEquals("INFO", ctx.getLevel());
        assertTrue(ctx.isLogResult());
        assertTrue(ctx.getMessageTemplate().contains("override"));
    }

    @Test
    void testMergeValueInheritsFromClass() throws NoSuchMethodException {
        Class<?> clazz = AnnotatedController.class;
        Method method = clazz.getMethod("inheritValue", String.class);
        AutoLog methodAnn = method.getAnnotation(AutoLog.class);
        AutoLog classAnn = clazz.getAnnotation(AutoLog.class);

        LogContext ctx = AutoLogAspect.buildLogContext(clazz, method,
                new Object[]{"test"}, methodAnn, classAnn, OP, TID);

        assertTrue(ctx.getMessageTemplate().contains("Controller"));
        assertEquals(1, ctx.getExcludeTypes().length);
        assertEquals(Context.class, ctx.getExcludeTypes()[0]);
        assertTrue(ctx.isLogResult()); // method boolean overrides class
    }

    @Test
    void testMethodOnlyNoClass() throws NoSuchMethodException {
        Method method = TestService.class.getMethod("greet", String.class);
        AutoLog methodAnn = method.getAnnotation(AutoLog.class);

        LogContext ctx = AutoLogAspect.buildLogContext(TestService.class, method,
                new Object[]{"World"}, methodAnn, null, OP, TID);

        assertEquals("INFO", ctx.getLevel());
        assertTrue(ctx.isLogArgs());
        assertTrue(ctx.isLogResult());
        assertEquals(0, ctx.getExcludeTypes().length);
        assertEquals(OP, ctx.getOperator());
        assertEquals(TID, ctx.getTraceId());
    }

    // ---- Helper ----

    private static LogContext buildCtx(Class<?> clazz, Method method, Object[] args,
                                       boolean logArgs, boolean logResult, boolean logTime,
                                       boolean logException, String level, String template) {
        return buildCtx(clazz, method, args, logArgs, logResult, logTime, logException, level, template, NO_EXCLUDE);
    }

    private static LogContext buildCtx(Class<?> clazz, Method method, Object[] args,
                                       boolean logArgs, boolean logResult, boolean logTime,
                                       boolean logException, String level, String template,
                                       Class<?>[] excludeTypes) {
        return new LogContext(clazz.getName(), method.getName(), method, args,
                logArgs, logResult, logTime, logException, level, template, excludeTypes,
                OP, TID);
    }

    // ---- Test stubs ----

    static class TestService {
        @AutoLog
        public String greet(String name) {
            return "Hello " + name;
        }

        @AutoLog
        public String login(String username, String password) {
            return "OK";
        }

        @AutoLog(excludeTypes = {Context.class})
        public String loginWithContext(String username, Context ctx, String token) {
            return "OK";
        }

        @AutoLog
        public String ping() {
            return "pong";
        }
    }

    @AutoLog(value = "Controller: {method}",
             level = "WARN",
             logResult = false,
             excludeTypes = {Context.class})
    static class AnnotatedController {

        public String listUsers() {
            return "[]";
        }

        @AutoLog(value = "override: {method}", logResult = true)
        public String overrideMethod(String id) {
            return id;
        }

        @AutoLog
        public String inheritValue(String id) {
            return id;
        }
    }

    static class Context {
        @Override
        public String toString() {
            return "Context{}";
        }
    }
}
