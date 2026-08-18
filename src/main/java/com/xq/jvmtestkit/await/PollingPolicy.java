package com.xq.jvmtestkit.await;

import java.time.Duration;
import java.util.Objects;

/**
 * The explicit time bounds used by a {@link Poller}.
 *
 * @param timeout the total time available to evaluate a condition
 * @param interval the maximum delay between unsuccessful evaluations
 */
public record PollingPolicy(Duration timeout, Duration interval) {

    public PollingPolicy {
        timeout = positive("timeout", timeout);
        interval = positive("interval", interval);
    }

    /**
     * Creates a policy with an explicit timeout and polling interval.
     *
     * @param timeout the total time available to evaluate a condition
     * @param interval the maximum delay between unsuccessful evaluations
     * @return an immutable polling policy
     */
    public static PollingPolicy of(Duration timeout, Duration interval) {
        return new PollingPolicy(timeout, interval);
    }

    private static Duration positive(String name, Duration duration) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return duration;
    }
}
