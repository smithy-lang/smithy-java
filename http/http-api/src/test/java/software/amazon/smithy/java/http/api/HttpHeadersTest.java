/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HttpHeadersTest {
    @Test
    public void caseInsensitiveHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("hi", List.of("a"));
        headers.put("BYE", List.of("b", "c"));
        var httpHeaders = HttpHeaders.of(headers);

        assertThat(httpHeaders.firstValue("hi"), equalTo("a"));
        assertThat(httpHeaders.firstValue("bye"), equalTo("b"));
        assertThat(httpHeaders.firstValue("BYE"), equalTo("b"));
        assertThat(httpHeaders.firstValue("byee"), nullValue());
    }

    @Test
    public void mergesHeadersOfDifferentCasing() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("hi", List.of("a"));
        headers.put("HI", List.of("b"));
        headers.put("Hi ", List.of("c"));
        var httpHeaders = HttpHeaders.of(headers);

        assertThat(httpHeaders.firstValue("hi"), equalTo("a"));
        assertThat(httpHeaders.allValues("hi"), contains("a", "b", "c"));
    }

    @Test
    public void convertUnmofiableToModifiable() {
        Map<String, List<String>> headers = Map.of("hi", List.of("a"));
        var httpHeaders = HttpHeaders.of(headers);
        var mod = httpHeaders.toModifiable();

        assertThat(httpHeaders.map(), equalTo(mod.map()));
    }

    @Test
    public void addHeaderTrustedBypassesNormalization() {
        var headers = new ArrayHttpHeaders();
        headers.addHeaderTrusted(HeaderName.CONTENT_LENGTH, "42");

        assertThat(headers.firstValue("content-length"), equalTo("42"));
    }

    @Test
    public void addHeaderTrustedUsesInternedName() {
        var headers = new ArrayHttpHeaders();
        headers.addHeaderTrusted(HeaderName.CONTENT_TYPE, "application/json");
        headers.addHeaderTrusted(HeaderName.CONTENT_LENGTH, "100");

        assertThat(headers.firstValue("content-type"), equalTo("application/json"));
        assertThat(headers.firstValue("content-length"), equalTo("100"));
    }

    @Test
    public void addHeaderTrustedCoexistsWithRegularHeaders() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader(HeaderName.of("x-custom"), "value1");
        headers.addHeaderTrusted(HeaderName.CONTENT_TYPE, "text/plain");
        headers.addHeader(HeaderName.of("x-other"), "value2");

        assertThat(headers.firstValue("x-custom"), equalTo("value1"));
        assertThat(headers.firstValue("content-type"), equalTo("text/plain"));
        assertThat(headers.firstValue("x-other"), equalTo("value2"));
    }
}
