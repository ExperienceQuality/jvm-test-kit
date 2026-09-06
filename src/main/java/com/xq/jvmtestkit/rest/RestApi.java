package com.xq.jvmtestkit.rest;

/** Executes service-relative REST operations for the current test invocation. */
public interface RestApi {
    RestResponse get(String path);

    RestResponse get(String path, RestRequest request);

    RestResponse post(String path, RestRequest request);

    RestResponse put(String path, RestRequest request);
}
