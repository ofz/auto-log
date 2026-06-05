package io.github.ofz.autolog.provider;

/**
 * Default implementation of {@link OperatorProvider} that always returns
 * {@code "system"}.
 *
 * <p>Replace this by registering a custom {@link OperatorProvider} bean, e.g.:
 * <pre>{@code
 * @Component
 * public class SecurityOperatorProvider implements OperatorProvider {
 *     public String getOperator() {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         return auth != null ? auth.getName() : "anonymous";
 *     }
 * }
 * }</pre>
 *
 * @author ofz
 */
public class DefaultOperatorProvider implements OperatorProvider {

    @Override
    public String getOperator() {
        return "system";
    }
}
