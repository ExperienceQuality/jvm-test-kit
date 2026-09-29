package com.xq.jvmtestkit.rest;

/** Fluent assertions for one immutable REST response. */
public interface RestAssertions {
    RestAssertions status(int expected);

    RestAssertions matchJson(String expectedJson);

    RestAssertions matchJson(Object expectedValue);
}
