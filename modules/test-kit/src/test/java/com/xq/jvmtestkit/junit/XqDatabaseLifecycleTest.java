package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.db.DatabaseClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class XqDatabaseLifecycleTest {
    @Test void inactiveAccessFailsWithoutCreatingClient() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, Xq::db);
        org.junit.jupiter.api.Assertions.assertTrue(failure.getMessage().contains("@XqTest"));
    }

    @XqTest
    static class Active {
        @Test void configuredNamedClientIsAvailableAndUnknownNameFails() {
            org.junit.jupiter.api.Assertions.assertNotNull(Xq.db().get("main"));
            assertThrows(IllegalArgumentException.class, () -> Xq.db().get("missing"));
        }
    }
}
