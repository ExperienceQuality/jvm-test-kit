package com.xq.jvmtestkit.contract;

public interface RestApi {
    RestApi get(String path);

    RestApi post(String path);

    RestMatcher should();


    interface RestMatcher {
        RestMatcher equalToJson(String json);

        RestMatcher equalToJson(Object json);

        RestMatcher equalToJsonSchema(String jsonSchema);

        RestMatcher match(String json);

        RestMatcher match(Object json);
    }
}

