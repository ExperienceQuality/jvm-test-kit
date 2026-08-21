package com.xq.jvmtestkit.impl;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.dto.RestResponse;

public class RestMatcherService implements RestApi.RestMatcher {
    private RestResponse restResponse = null;
    public RestMatcherService(RestResponse restResponse) {
        this.restResponse = restResponse;
    }
    @Override
    public RestApi.RestMatcher equalToJson(String json) {
        return this;
    }

    @Override
    public RestApi.RestMatcher equalToJson(Object json) {
        return null;
    }

    @Override
    public RestApi.RestMatcher equalToJsonSchema(String jsonSchema) {
        return null;
    }

    @Override
    public RestApi.RestMatcher match(String json) {
        return null;
    }

    @Override
    public RestApi.RestMatcher match(Object json) {
        return null;
    }
}
