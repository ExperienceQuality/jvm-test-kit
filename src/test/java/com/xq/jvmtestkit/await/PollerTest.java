package com.xq.jvmtestkit.await;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PollerTest {
    @Test
    void succeedsWhenTheConditionEventuallyBecomesSatisfied() {
        AtomicInteger attempts = new AtomicInteger();
        Poller poller = new Poller(PollingPolicy.of(Duration.ofSeconds(1), Duration.ofMillis(1)));

        assertDoesNotThrow(() -> poller.await(() -> attempts.incrementAndGet() == 3));

        assertEquals(3, attempts.get());
    }

    @Test
    void rejectsMissingOrNonPositiveBounds() {
        assertThrows(NullPointerException.class, () -> PollingPolicy.of(null, Duration.ofMillis(1)));
        assertThrows(NullPointerException.class, () -> PollingPolicy.of(Duration.ofMillis(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> PollingPolicy.of(Duration.ZERO, Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class,
                () -> PollingPolicy.of(Duration.ofMillis(1), Duration.ofMillis(-1)));
    }

    @Test
    void timesOutWithinTheConfiguredBoundWithSanitizedEvidence() {
        Poller poller = new Poller(PollingPolicy.of(Duration.ofMillis(30), Duration.ofMillis(5)));
        String secret = "token=do-not-disclose";
        CheckedCondition condition = new CheckedCondition() {
            @Override
            public boolean isSatisfied() {
                return false;
            }

            @Override
            public String toString() {
                return secret;
            }
        };

        TimeoutException timeout = assertThrows(TimeoutException.class, () -> poller.await(condition));

        assertTrue(timeout.getMessage().contains("attempt(s)"));
        assertFalse(timeout.getMessage().contains(secret));
    }

    @Test
    void preservesTheInterruptedStatusWhenSleepingIsInterrupted() {
        Poller poller = new Poller(PollingPolicy.of(Duration.ofSeconds(1), Duration.ofMillis(10)));
        Thread.currentThread().interrupt();

        try {
            assertThrows(InterruptedException.class, () -> poller.await(() -> false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void preservesTheInterruptedStatusWhenTheConditionIsInterrupted() {
        Poller poller = new Poller(PollingPolicy.of(Duration.ofSeconds(1), Duration.ofMillis(10)));

        try {
            assertThrows(InterruptedException.class, () -> poller.await(() -> {
                throw new InterruptedException("secret");
            }));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void doesNotExposeAConditionFailureMessage() {
        Poller poller = new Poller(PollingPolicy.of(Duration.ofSeconds(1), Duration.ofMillis(10)));
        String secret = "password=do-not-disclose";

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> poller.await(() -> {
                    throw new Exception(secret);
                }));

        assertEquals("Polling condition evaluation failed", failure.getMessage());
        assertNull(failure.getCause());
    }
}
