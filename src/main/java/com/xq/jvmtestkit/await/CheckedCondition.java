package com.xq.jvmtestkit.await;

/**
 * A condition that can be evaluated by a {@link Poller} and may fail with a
 * checked exception.
 */
@FunctionalInterface
public interface CheckedCondition {

    /**
     * Evaluates this condition.
     *
     * @return {@code true} when polling should stop successfully
     * @throws Exception when the condition cannot be evaluated
     */
    boolean isSatisfied() throws Exception;
}
