package com.xq.jvmtestkit.rest;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.assertj.core.api.AbstractAssert;

import java.util.Objects;

import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_FIELDS;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_EXTRA_ARRAY_ITEMS;

public final class RestResponseAssert extends AbstractAssert<RestResponseAssert, RestResponse> {
    public static RestResponseAssert assertThat(RestResponse actual) {
        return new RestResponseAssert(actual);
    }

    public RestResponseAssert(RestResponse actual) {
        super(actual, RestResponseAssert.class);
    }

    public RestResponseAssert hasStatus(int expected) {
        isNotNull();
        if (actual.statusCode() != expected) failWithMessage("expected status <%s> but was <%s>", expected, actual.statusCode());
        return this;
    }

    public RestResponseAssert hasHeader(String name) {
        isNotNull();
        if (actual.headers().keySet().stream().noneMatch(key -> key.equalsIgnoreCase(name))) failWithMessage("expected header <%s>", name);
        return this;
    }

    public RestResponseAssert hasHeader(String name, String expected) {
        isNotNull();
        if (actual.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .flatMap(e -> e.getValue().stream()).noneMatch(expected::equals)) failWithMessage("expected header <%s> with value <%s>", name, expected);
        return this;
    }

    public RestResponseAssert hasEmptyBody() {
        isNotNull();
        if (actual.body().length != 0) failWithMessage("expected empty body but had <%s> bytes", actual.body().length);
        return this;
    }

    public RestResponseAssert hasBody(String expected) {
        isNotNull();
        if (!Objects.equals(actual.bodyUtf8(), expected)) failWithMessage("expected body <%s> but was <%s>", expected, "<redacted>");
        return this;
    }

    public RestResponseAssert hasJsonBody(Object expected) {
        isNotNull();
        try { JsonAssertions.assertThatJson(actual.bodyUtf8()).isEqualTo(expected); }
        catch (AssertionError error) { failWithMessage("expected response JSON to match; status=%s; body=<redacted:%s bytes>", actual.statusCode(), actual.body().length); }
        return this;
    }

    public RestResponseAssert containsJson(Object expected) {
        isNotNull();
        try { JsonAssertions.assertThatJson(actual.bodyUtf8()).when(IGNORING_EXTRA_FIELDS, IGNORING_EXTRA_ARRAY_ITEMS, IGNORING_ARRAY_ORDER).isEqualTo(expected); }
        catch (AssertionError error) { failWithMessage("expected response JSON to contain supplied structure; status=%s; body=<redacted:%s bytes>", actual.statusCode(), actual.body().length); }
        return this;
    }

    public RestResponseAssert hasJsonPathValue(String path, Object expected) {
        isNotNull();
        try { JsonAssertions.assertThatJson(actual.bodyUtf8()).inPath(path).isEqualTo(expected); }
        catch (AssertionError error) { failWithMessage("expected JSON path <%s> to match; status=%s; body=<redacted:%s bytes>", path, actual.statusCode(), actual.body().length); }
        return this;
    }

    /** Asserts that a JSON path contains the supplied structure, allowing extra object fields and array items. */
    public RestResponseAssert containsJsonAtPath(String path, Object expected) {
        isNotNull();
        try {
            JsonAssertions.assertThatJson(actual.bodyUtf8())
                    .when(IGNORING_EXTRA_FIELDS, IGNORING_EXTRA_ARRAY_ITEMS, IGNORING_ARRAY_ORDER)
                    .inPath(path)
                    .isEqualTo(expected);
        } catch (AssertionError error) {
            failWithMessage("expected JSON path <%s> to contain supplied structure; status=%s; body=<redacted:%s bytes>",
                    path, actual.statusCode(), actual.body().length);
        }
        return this;
    }
}
