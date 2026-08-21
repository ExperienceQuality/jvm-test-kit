package com.xq.jvmtestkit.impl;

import com.xq.jvmtestkit.contract.RestApi;
import com.xq.jvmtestkit.dto.RestResponse;

public class RestApiService implements RestApi {
    private RestMatcher matcher;

    public RestApiService() {

    }

    @Override
    public RestApi get(String path) {
        return null;
    }

    @Override
    public RestApi post(String path) {
        return null;
    }

    @Override
    public RestMatcher should() {
        return this.matcher;
    }

    private void pipe(RestResponse res) {
        this.matcher = new RestMatcherService(res);
    }
}
