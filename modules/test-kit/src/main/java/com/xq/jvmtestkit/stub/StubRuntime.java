package com.xq.jvmtestkit.stub;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/** Internal shared server and scenario isolation implementation. */
public final class StubRuntime {
    static final String TEST_ID_HEADER = StubApi.TEST_ID_HEADER;
    private static final String SCENARIO_METADATA = "xqScenarioId";
    private static final Object LOCK = new Object();
    private static final ReentrantLock SERIAL_LEASE = new ReentrantLock();
    private static SharedServer shared;

    private StubRuntime() {
    }

    public static StubApi open(String scenarioId, String host, int port, boolean resetBeforeScenario,
                               boolean isolateScenarios) {
        Objects.requireNonNull(scenarioId, "scenarioId");
        return new ScenarioStub(scenarioId, new Settings(host, port, resetBeforeScenario, isolateScenarios));
    }

    private static SharedServer start(Settings settings) {
        synchronized (LOCK) {
            if (shared != null) {
                if (!shared.settings.equals(settings)) {
                    throw new IllegalStateException("XQ stub is already running with different configuration");
                }
                return shared;
            }
            WireMockConfiguration configuration = WireMockConfiguration.wireMockConfig()
                    .bindAddress(settings.host());
            if (settings.port() == 0) configuration.dynamicPort();
            else configuration.port(settings.port());
            WireMockServer server = new WireMockServer(configuration);
            try {
                server.start();
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Could not start XQ stub on " + settings.host() + ":" + settings.port(), exception);
            }
            shared = new SharedServer(settings, server);
            return shared;
        }
    }

    public static void shutdown() {
        synchronized (LOCK) {
            if (shared != null) {
                shared.server.stop();
                shared = null;
            }
        }
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(StubRuntime::shutdown, "xq-stub-shutdown"));
    }

    private record Settings(String host, int port, boolean resetBeforeScenario, boolean isolateScenarios) {
    }

    private record SharedServer(Settings settings, WireMockServer server) {
    }

    private static final class ScenarioStub implements StubApi {
        private final String scenarioId;
        private final Settings settings;
        private final AtomicBoolean closed = new AtomicBoolean();
        private boolean leaseHeld;
        private SharedServer server;

        private ScenarioStub(String scenarioId, Settings settings) {
            this.scenarioId = scenarioId;
            this.settings = settings;
        }

        @Override
        public StubApi start() {
            ensureOpen();
            if (server != null) return this;
            if (!settings.isolateScenarios()) {
                if (!SERIAL_LEASE.tryLock()) {
                    throw new IllegalStateException("XQ stub scenario overlap requires xq.stub.isolate-scenarios=true");
                }
                leaseHeld = true;
            }
            try {
                server = StubRuntime.start(settings);
                if (settings.resetBeforeScenario()) reset();
                return this;
            } catch (RuntimeException exception) {
                releaseLease();
                throw exception;
            }
        }

        @Override
        public StubMapping stubFor(MappingBuilder mapping) {
            ensureStarted();
            StubMapping result = Objects.requireNonNull(mapping, "mapping").build();
            decorate(result);
            server.server.addStubMapping(result);
            return result;
        }

        @Override
        public StubMapping failConnection(RequestPatternBuilder request) {
            ensureStarted();
            RequestPattern pattern = scopedRequest(Objects.requireNonNull(request, "request").build());
            StubMapping result = new StubMapping(pattern,
                    WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER).build());
            decorate(result);
            server.server.addStubMapping(result);
            return result;
        }

        @Override
        public void verify(RequestPatternBuilder request) {
            ensureStarted();
            server.server.verify(scopedBuilder(request));
        }

        @Override
        public void verify(int count, RequestPatternBuilder request) {
            ensureStarted();
            server.server.verify(count, scopedBuilder(request));
        }

        @Override
        public StubApi reset() {
            ensureStarted();
            resetMappings();
            resetRequests();
            return this;
        }

        @Override
        public StubApi resetMappings() {
            ensureStarted();
            if (settings.isolateScenarios()) {
                server.server.removeStubMappingsByMetadata(
                        WireMock.matchingJsonPath("$." + SCENARIO_METADATA, WireMock.equalTo(scenarioId)));
            } else {
                server.server.resetMappings();
            }
            return this;
        }

        @Override
        public StubApi resetRequests() {
            ensureStarted();
            if (settings.isolateScenarios()) {
                server.server.removeServeEventsMatching(scopedRequestBuilder().build());
            } else {
                server.server.resetRequests();
            }
            return this;
        }

        @Override
        public URI baseUri() {
            ensureStarted();
            return URI.create(server.server.baseUrl() + "/");
        }

        @Override
        public void close() {
            if (closed.get()) return;
            if (server != null) {
                try {
                    reset();
                } finally {
                    closed.set(true);
                    server = null;
                    releaseLease();
                }
            } else {
                closed.set(true);
                releaseLease();
            }
        }

        private void decorate(StubMapping mapping) {
            if (settings.isolateScenarios()) {
                mapping.setRequest(scopedRequest(mapping.getRequest()));
                mapping.setMetadata(new Metadata(Map.of(SCENARIO_METADATA, scenarioId)));
            }
        }

        private RequestPatternBuilder scopedBuilder(RequestPatternBuilder input) {
            return scopedRequestBuilder(input == null ? null : input.build());
        }

        private RequestPattern scopedRequest(RequestPattern input) {
            return scopedRequestBuilder(input).build();
        }

        private RequestPatternBuilder scopedRequestBuilder() {
            return scopedRequestBuilder(null);
        }

        private RequestPatternBuilder scopedRequestBuilder(RequestPattern input) {
            RequestPatternBuilder builder = input == null
                    ? RequestPatternBuilder.allRequests()
                    : RequestPatternBuilder.like(input);
            if (settings.isolateScenarios()) builder.withHeader(TEST_ID_HEADER, WireMock.equalTo(scenarioId));
            return builder;
        }

        private void ensureStarted() {
            ensureOpen();
            if (server == null) start();
        }

        private void ensureOpen() {
            if (closed.get()) throw new IllegalStateException("XQ stub API is closed");
        }

        private void releaseLease() {
            if (leaseHeld) {
                leaseHeld = false;
                SERIAL_LEASE.unlock();
            }
        }
    }
}
