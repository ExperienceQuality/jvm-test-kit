package com.xq.jvmtestkit.await;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Evaluates conditions repeatedly within an explicit {@link PollingPolicy}.
 *
 * <p>The timeout diagnostic intentionally contains only elapsed timing and
 * attempt-count information. It never renders the condition or an exception
 * raised by it, as either can contain credentials or service data.</p>
 */
public final class Poller {
    private final PollingPolicy policy;

    /**
     * Creates a poller using the supplied explicit policy.
     *
     * @param policy the bounds to apply to every await operation
     */
    public Poller(PollingPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * Polls until the condition succeeds or the configured timeout expires.
     *
     * @param condition the condition to evaluate
     * @throws TimeoutException when no evaluation succeeds within the policy timeout
     * @throws InterruptedException when the calling thread is interrupted; its
     *     interrupted status is preserved
     */
    public void await(CheckedCondition condition) throws TimeoutException, InterruptedException {
        Objects.requireNonNull(condition, "condition must not be null");

        long startedAt = System.nanoTime();
        long timeoutNanos = saturatingNanos(policy.timeout());
        long deadline = saturatedAdd(startedAt, timeoutNanos);
        int attempts = 0;

        while (true) {
            attempts++;
            if (evaluate(condition)) {
                return;
            }

            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw timeout(attempts, elapsedSince(startedAt));
            }

            sleepAtMost(remainingNanos);
        }
    }

    /**
     * Polls with the supplied policy without requiring callers to retain a
     * {@code Poller} instance.
     *
     * @param condition the condition to evaluate
     * @param policy the explicit bounds to apply
     * @throws TimeoutException when no evaluation succeeds within the policy timeout
     * @throws InterruptedException when the calling thread is interrupted
     */
    public static void await(CheckedCondition condition, PollingPolicy policy)
            throws TimeoutException, InterruptedException {
        new Poller(policy).await(condition);
    }

    private static boolean evaluate(CheckedCondition condition) throws InterruptedException {
        try {
            return condition.isSatisfied();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Polling condition evaluation failed");
        }
    }

    private void sleepAtMost(long remainingNanos) throws InterruptedException {
        long pauseNanos = Math.min(saturatingNanos(policy.interval()), remainingNanos);
        try {
            Thread.sleep(Duration.ofNanos(pauseNanos));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private static TimeoutException timeout(int attempts, Duration elapsed) {
        return new TimeoutException(
                "Polling condition was not satisfied within " + elapsed.toMillis()
                        + " ms after " + attempts + " attempt(s)");
    }

    private static Duration elapsedSince(long startedAt) {
        long elapsed = System.nanoTime() - startedAt;
        return Duration.ofNanos(Math.max(0, elapsed));
    }

    private static long saturatingNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
