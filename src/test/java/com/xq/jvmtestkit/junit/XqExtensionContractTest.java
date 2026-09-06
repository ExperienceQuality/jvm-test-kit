package com.xq.jvmtestkit.junit;

import com.xq.jvmtestkit.rest.RestApi;
import com.xq.jvmtestkit.rest.RestApiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class XqExtensionContractTest {
    @Test
    void createsFreshContextsAndClosesCapturedHelpers() {
        Probe.apis.clear();
        SummaryGeneratingListener listener = execute(SuccessfulConsumer.class);

        assertEquals(0, listener.getSummary().getFailures().size());
        assertEquals(2, Probe.apis.size());
        Probe.apis.forEach(api -> assertTrue(
                assertThrows(IllegalStateException.class, () -> api.get("/closed")).getMessage().contains("closed")
        ));
    }

    @Test
    void rejectsInactiveAndUnconfiguredAccessAndDoesNotPropagateToWorkerThreads() {
        SummaryGeneratingListener listener = execute(UnconfiguredConsumer.class, WorkerThreadConsumer.class);

        assertEquals(0, listener.getSummary().getFailures().size());
        assertTrue(assertThrows(IllegalStateException.class, Xq::rest).getMessage().contains("@XqTest"));
    }

    private static SummaryGeneratingListener execute(Class<?>... classes) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(java.util.Arrays.stream(classes).map(org.junit.platform.engine.discovery.DiscoverySelectors::selectClass).toList())
                .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        return listener;
    }

    @XqTest
    static class SuccessfulConsumer {
        private RestApi previous;

        @BeforeEach
        void configure() {
            RestApi current = Xq.rest(RestApiConfig.at(URI.create("http://127.0.0.1:1/")));
            if (previous != null) {
                assertNotSame(previous, current);
            }
            previous = current;
            Probe.apis.add(current);
        }

        @Test
        void first() {
            assertSame(previous, Xq.rest());
        }

        @Test
        void second() {
            assertSame(previous, Xq.rest());
        }
    }

    @XqTest
    static class UnconfiguredConsumer {
        @Test
        void rejectsUnconfiguredAccess() {
            assertTrue(assertThrows(IllegalStateException.class, Xq::rest).getMessage().contains("@BeforeEach"));
        }
    }

    @XqTest
    static class WorkerThreadConsumer {
        @BeforeEach
        void configure() {
            Xq.rest(RestApiConfig.at(URI.create("http://127.0.0.1:1/")));
        }

        @Test
        void workerHasNoAmbientContext() throws Exception {
            try (var executor = Executors.newSingleThreadExecutor()) {
                Throwable failure = executor.submit(() -> {
                    try {
                        Xq.rest();
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }).get();
                assertTrue(failure instanceof IllegalStateException);
            }
        }
    }

    private static final class Probe {
        private static final Set<RestApi> apis = ConcurrentHashMap.newKeySet();
    }
}
