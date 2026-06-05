package io.github.ofz.autolog.provider;

/**
 * Strategy interface for resolving the current operator (user) associated
 * with a log entry. Implement this interface and register a Spring bean to
 * provide a custom operator resolution strategy.
 *
 * <p>The default implementation {@code DefaultOperatorProvider} returns
 * {@code "system"}.
 *
 * @author ofz
 */
@FunctionalInterface
public interface OperatorProvider {

    /**
     * Returns the current operator identifier (e.g. username, system account).
     * This method is called at log-collection time (on the application thread,
     * before the event is published to Disruptor).
     *
     * @return the operator string, never null
     */
    String getOperator();
}
