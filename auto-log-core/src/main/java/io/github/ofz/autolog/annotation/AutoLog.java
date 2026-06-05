package io.github.ofz.autolog.annotation;

import java.lang.annotation.*;

/**
 * Auto-logging annotation for methods and classes.
 *
 * <p>When placed on a <b>class</b>, all public methods in the class are auto-logged.
 * When placed on a <b>method</b>, only that method is logged. A method-level
 * {@code @AutoLog} takes precedence over the class-level one.
 *
 * <h3>Class-level usage</h3>
 * <pre>{@code
 * @AutoLog(excludeTypes = {HttpServletRequest.class, HttpServletResponse.class})
 * @RestController
 * public class UserController {
 *
 *     // Inherits class-level excludeTypes, uses default format
 *     public User getUser(String id) { ... }
 *
 *     // Overrides message template, inherits excludeTypes from class
 *     @AutoLog("Login: {method} | {time}ms")
 *     public User login(String username, String password) { ... }
 *
 *     // Overrides logResult, rest inherits from class
 *     @AutoLog(logResult = false)
 *     public User update(String id, UserDto dto) { ... }
 * }
 * }</pre>
 *
 * <h3>Method-level usage</h3>
 * <pre>{@code
 * public class UserService {
 *
 *     @AutoLog(value = "User login", logArgs = true, logResult = false)
 *     public User login(String username, String password) { ... }
 * }
 * }</pre>
 *
 * <h3>Merge rules</h3>
 * When both class and method carry {@code @AutoLog}:
 * <ul>
 *   <li>{@code value()} — method non-empty wins, else falls back to class</li>
 *   <li>{@code excludeTypes()} — method non-empty wins, else falls back to class</li>
 *   <li>{@code logArgs/logResult/logTime/logException/level} — method always wins</li>
 * </ul>
 *
 * <p>Available placeholders for {@link #value()}:
 * <ul>
 *   <li>{@code {class}} - fully qualified class name</li>
 *   <li>{@code {method}} - method name</li>
 *   <li>{@code {args}} - method arguments</li>
 *   <li>{@code {result}} - method return value</li>
 *   <li>{@code {time}} - execution time in milliseconds</li>
 *   <li>{@code {status}} - execution status (SUCCESS / FAILURE)</li>
 *   <li>{@code {operator}} - current operator/user (via {@code OperatorProvider})</li>
 *   <li>{@code {traceId}} - distributed trace ID (via {@code TraceIdProvider})</li>
 * </ul>
 *
 * @author ofz
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AutoLog {

    /**
     * Custom log message template.
     * When empty, the default format configured via {@link io.github.ofz.autolog.formatter.LogFormatter} is used.
     * <p>Supported placeholders: {class}, {method}, {args}, {result}, {time}, {status}
     */
    String value() default "";

    /**
     * Whether to include method arguments in the log output. Defaults to {@code true}.
     */
    boolean logArgs() default true;

    /**
     * Whether to include method return value in the log output. Defaults to {@code true}.
     */
    boolean logResult() default true;

    /**
     * Whether to include execution time in the log output. Defaults to {@code true}.
     */
    boolean logTime() default true;

    /**
     * Whether to include exception details in the log output. Defaults to {@code true}.
     */
    boolean logException() default true;

    /**
     * Log level for the output. Defaults to {@code "INFO"}.
     * <p>Supported values: TRACE, DEBUG, INFO, WARN, ERROR.
     */
    String level() default "INFO";

    /**
     * Argument types to exclude from logging. When formatting method arguments,
     * any argument whose type is assignable to a type in this array is skipped.
     * Useful for excluding framework objects such as {@code HttpServletRequest},
     * {@code HttpServletResponse}, {@code MultipartFile}, etc.
     * <p>Example: {@code excludeTypes = {HttpServletRequest.class, HttpServletResponse.class}}
     */
    Class<?>[] excludeTypes() default {};

    /**
     * SpEL expression that determines whether this invocation should be logged.
     * Evaluated after method execution; the evaluation context provides:
     * <ul>
     *   <li>Method parameters by name (e.g. {@code #username}, {@code #id})</li>
     *   <li>{@code #result} — the return value ({@code null} for void / on exception)</li>
     *   <li>{@code #exception} — the thrown exception ({@code null} on success)</li>
     * </ul>
     * The expression must evaluate to a {@code boolean}. If {@code false},
     * the log event is discarded (the {@code LogContext} is returned to the pool
     * without publishing).
     *
     * <p>Examples:
     * <pre>{@code
     * // Only log when the result is non-null
     * @AutoLog(condition = "#result != null")
     *
     * // Skip logging for the admin user
     * @AutoLog(condition = "#username != 'admin'")
     *
     * // Only log failed invocations
     * @AutoLog(condition = "#exception != null")
     * }</pre>
     *
     * <p>Defaults to {@code "true"} (always log). If evaluation fails the
     * invocation is logged as a safety measure.
     */
    String condition() default "true";

    /**
     * Method parameter names whose values should be masked in log output.
     * Parameters listed here have their values replaced with {@code ***}
     * while the parameter name itself is still shown.
     *
     * <p>Works in conjunction with the global {@link io.github.ofz.autolog.provider.SensitiveDataFilter}
     * SPI — both the annotation-level names and the filter's keyword matches
     * are applied. Use this for method-specific overrides when the global
     * keyword list is insufficient.
     *
     * <p>Example: {@code sensitiveParams = {"creditCard", "ssn"}}
     */
    String[] sensitiveParams() default {};
}
