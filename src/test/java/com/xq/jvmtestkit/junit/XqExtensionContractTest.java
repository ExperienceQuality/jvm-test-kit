package com.xq.jvmtestkit.junit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.contract.RestApiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

class XqExtensionContractTest {
    private HttpServer server;

    @BeforeEach
    void startServerAndResetProbes() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", XqExtensionContractTest::respondOk);
        server.start();
        RestApiConfig config = RestApiConfig.at(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")
        );
        TestProbe.reset(config);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void keepsHookConfiguredRestHelperThroughTestAndTeardownThenClosesIt() {
        SummaryGeneratingListener listener = execute(selectClass(SuccessfulConsumer.class));

        assertEquals(0, listener.getSummary().getFailures().size());
        assertEquals(2, TestProbe.restApiInstances.size());
        TestProbe.restApiInstances.forEach(XqExtensionContractTest::assertClosed);
    }

    @Test
    void givesParameterizedAndPerClassInvocationsFreshContexts() {
        SummaryGeneratingListener parameterized = execute(selectClass(ParameterizedConsumer.class));
        assertEquals(0, parameterized.getSummary().getFailures().size());
        assertEquals(2, TestProbe.restApiInstances.size());

        TestProbe.reset(TestProbe.config);
        SummaryGeneratingListener perClass = execute(selectClass(PerClassConsumer.class));
        assertEquals(0, perClass.getSummary().getFailures().size());
        assertEquals(2, TestProbe.restApiInstances.size());
    }

    @Test
    void cleansUpAfterFailingTestAndFailingHook() {
        SummaryGeneratingListener failingTest = execute(selectClass(FailingConsumer.class));
        assertEquals(1, failingTest.getSummary().getFailures().size());
        RestApi testApi = TestProbe.lastApi;
        assertClosed(testApi);

        TestProbe.reset(TestProbe.config);
        SummaryGeneratingListener failingHook = execute(selectClass(FailingHookConsumer.class));
        assertEquals(1, failingHook.getSummary().getFailures().size());
        assertClosed(TestProbe.lastApi);
    }

    @Test
    void rejectsConflictingConfigurationAndUnconfiguredOrInactiveAccess() {
        SummaryGeneratingListener listener = execute(
                selectClass(ConfigurationConsumer.class),
                selectClass(UnconfiguredConsumer.class)
        );
        assertEquals(0, listener.getSummary().getFailures().size());

        IllegalStateException inactive = assertThrows(IllegalStateException.class, Xq::rest);
        assertTrue(inactive.getMessage().contains("@XqTest"));
    }

    @Test
    void doesNotPropagateContextToUserCreatedThreads() {
        SummaryGeneratingListener listener = execute(selectClass(WorkerThreadConsumer.class));

        assertEquals(0, listener.getSummary().getFailures().size());
    }

    @Test
    void isolatesParallelTestClasses() {
        TestProbe.parallelReady = new CountDownLatch(2);
        SummaryGeneratingListener listener = executeInParallel(
                selectClass(FirstParallelConsumer.class),
                selectClass(SecondParallelConsumer.class)
        );

        assertEquals(0, listener.getSummary().getFailures().size());
        assertEquals(2, TestProbe.restApiInstances.size());
    }

    private static SummaryGeneratingListener execute(DiscoverySelector... selectors) {
        return execute(LauncherDiscoveryRequestBuilder.request().selectors(selectors).build());
    }

    private static SummaryGeneratingListener executeInParallel(DiscoverySelector... selectors) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "same_thread")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "2")
                .build();
        return execute(request);
    }

    private static SummaryGeneratingListener execute(LauncherDiscoveryRequest request) {
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        return listener;
    }

    private static void respondOk(HttpExchange exchange) throws IOException {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void assertClosed(RestApi api) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, api::should);
        assertTrue(failure.getMessage().contains("closed"));
    }

    @XqTest
    static class SuccessfulConsumer {
        private RestApi hookApi;

        @BeforeEach
        void configureRestApi() {
            hookApi = Xq.rest(TestProbe.config);
            TestProbe.restApiInstances.add(hookApi);
        }

        @Test
        void usesSameRestApiAndRealHttpTransport() {
            assertSame(hookApi, Xq.rest());
            Xq.rest().get("/health").should();
        }

        @Test
        void createsAFreshHelperForAnotherInvocation() {
            assertSame(hookApi, Xq.rest());
        }

        @AfterEach
        void remainsAvailableDuringUserTeardown() {
            assertSame(hookApi, Xq.rest());
        }
    }

    @XqTest
    static class ParameterizedConsumer {
        private RestApi previous;

        @BeforeEach
        void configure() {
            RestApi current = Xq.rest(TestProbe.config);
            TestProbe.restApiInstances.add(current);
            if (previous != null) {
                assertNotSame(previous, current);
            }
            previous = current;
        }

        @ParameterizedTest
        @ValueSource(strings = {"create", "cancel"})
        void receivesFreshContext(String operation) {
            assertTrue(operation.length() > 3);
        }
    }

    @XqTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassConsumer {
        private RestApi previous;

        @BeforeEach
        void configure() {
            RestApi current = Xq.rest(TestProbe.config);
            TestProbe.restApiInstances.add(current);
            if (previous != null) {
                assertNotSame(previous, current);
            }
            previous = current;
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
    static class FailingConsumer {
        @BeforeEach
        void configure() {
            TestProbe.lastApi = Xq.rest(TestProbe.config);
        }

        @Test
        void fails() {
            fail("expected test failure");
        }
    }

    @XqTest
    static class FailingHookConsumer {
        @BeforeEach
        void configureThenFail() {
            TestProbe.lastApi = Xq.rest(TestProbe.config);
            fail("expected hook failure");
        }

        @Test
        void neverRuns() {
            fail("test must not run after a failing hook");
        }
    }

    @XqTest
    static class ConfigurationConsumer {
        @Test
        void enforcesSingleApiConfigurationRules() {
            RestApi configured = Xq.rest(TestProbe.config);
            assertSame(configured, Xq.rest(TestProbe.config));

            RestApiConfig conflict = RestApiConfig.at(URI.create("https://example.test/"));
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> Xq.rest(conflict)
            );
            assertTrue(failure.getMessage().contains("already configured differently"));
        }
    }

    @XqTest
    static class UnconfiguredConsumer {
        @Test
        void requiresConfigurationBeforeAccess() {
            IllegalStateException failure = assertThrows(IllegalStateException.class, Xq::rest);
            assertTrue(failure.getMessage().contains("Xq.rest(config)"));
        }
    }

    @XqTest
    static class WorkerThreadConsumer {
        @BeforeEach
        void configure() {
            TestProbe.lastApi = Xq.rest(TestProbe.config);
        }

        @Test
        void childThreadHasNoAmbientContext() throws Exception {
            try (var executor = Executors.newSingleThreadExecutor()) {
                Throwable failure = executor.submit(() -> {
                    try {
                        Xq.rest();
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }).get(5, TimeUnit.SECONDS);

                assertInstanceOf(IllegalStateException.class, failure);
                assertSame(TestProbe.lastApi, Xq.rest());
            }
        }
    }

    @XqTest
    static class FirstParallelConsumer extends ParallelConsumer {
    }

    @XqTest
    static class SecondParallelConsumer extends ParallelConsumer {
    }

    abstract static class ParallelConsumer {
        @Test
        void overlapsWithoutSharingContext() throws InterruptedException {
            RestApi api = Xq.rest(TestProbe.config);
            TestProbe.restApiInstances.add(api);
            TestProbe.parallelReady.countDown();
            assertTrue(TestProbe.parallelReady.await(5, TimeUnit.SECONDS), "parallel classes did not overlap");
        }
    }

    private static final class TestProbe {
        private static final Set<RestApi> restApiInstances = ConcurrentHashMap.newKeySet();
        private static volatile RestApiConfig config;
        private static volatile RestApi lastApi;
        private static volatile CountDownLatch parallelReady = new CountDownLatch(0);

        private TestProbe() {
        }

        static void reset(RestApiConfig nextConfig) {
            restApiInstances.clear();
            config = nextConfig;
            lastApi = null;
            parallelReady = new CountDownLatch(0);
        }
    }
}
