package com.xq.jvmtestkit.stub;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import java.net.URI;

/** Scenario-scoped access to the XQ downstream HTTP stub. */
public interface StubApi extends AutoCloseable {
    String TEST_ID_HEADER = "X-Xq-Test-Id";

    StubApi start();

    StubMapping stubFor(MappingBuilder mapping);

    StubMapping failConnection(RequestPatternBuilder request);

    void verify(RequestPatternBuilder request);

    void verify(int count, RequestPatternBuilder request);

    StubApi reset();

    StubApi resetMappings();

    StubApi resetRequests();

    URI baseUri();

    @Override
    void close();
}
