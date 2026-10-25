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

    @Test
    public void setHeaderReplacesSingleExistingValue() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("y", "2");
        headers.setHeader("X", "9");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.allValues("x"), contains("9"));
        assertThat(headers.map().keySet(), contains("x", "y"));
    }

    @Test
    public void setHeaderAppendsWhenAbsent() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.setHeader("y", "2");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.map().keySet(), contains("x", "y"));
        assertThat(headers.firstValue("y"), equalTo("2"));
    }

    @Test
    public void setHeaderCollapsesDuplicatesAndPreservesOrder() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("x", "2");
        headers.addHeader("y", "3");
        headers.addHeader("x", "4");
        headers.setHeader("x", "9");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.allValues("x"), contains("9"));
        assertThat(headers.allValues("y"), contains("3"));
        assertThat(headers.map().keySet(), contains("x", "y"));
    }

    @Test
    public void setHeaderCollapsesDuplicateInLastSlot() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("y", "2");
        headers.addHeader("x", "3");
        headers.setHeader("x", "9");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.allValues("x"), contains("9"));
        assertThat(headers.map().keySet(), contains("x", "y"));

        headers.addHeader("z", "4");
        assertThat(headers.size(), equalTo(3));
        assertThat(headers.map().keySet(), contains("x", "y", "z"));
    }

    @Test
    public void setHeaderWithListReplacesAllOccurrences() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("y", "2");
        headers.addHeader("x", "3");
        headers.setHeader("x", List.of("8", "9"));

        assertThat(headers.size(), equalTo(3));
        assertThat(headers.allValues("x"), contains("8", "9"));
        assertThat(headers.map().keySet(), contains("y", "x"));
    }

    @Test
    public void removeHeaderRemovesAllOccurrencesAndPreservesOrder() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("y", "2");
        headers.addHeader("x", "3");
        headers.addHeader("z", "4");
        headers.removeHeader("X");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.hasHeader("x"), equalTo(false));
        assertThat(headers.map().keySet(), contains("y", "z"));
    }

    @Test
    public void removeHeaderIsNoOpWhenAbsent() {
        var headers = new ArrayHttpHeaders();
        headers.addHeader("x", "1");
        headers.addHeader("y", "2");
        headers.removeHeader("z");

        assertThat(headers.size(), equalTo(2));
        assertThat(headers.map().keySet(), contains("x", "y"));
    }
}
