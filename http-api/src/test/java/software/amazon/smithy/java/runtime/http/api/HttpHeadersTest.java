/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

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
}
