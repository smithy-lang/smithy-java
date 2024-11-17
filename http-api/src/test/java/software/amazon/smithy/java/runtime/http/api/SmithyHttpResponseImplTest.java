/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SmithyHttpResponseImplTest {
    @Test
    public void addHeaders() {
        var response = HttpResponse.builder()
            .statusCode(200)
            .withAddedHeaders("foo", "bar   ", "Baz", "bam", "FOO", "bar2")
            .build();

        assertThat(response.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz"));
    }

    @Test
    public void addHeadersToExistingHeaders() {
        var response = HttpResponse.builder()
            .statusCode(200)
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withAddedHeaders("foo", "bar   ", "Baz", "bam", "FOO", "bar2")
            .build();

        assertThat(response.headers().allValues("foo"), contains("bar0", "bar", "bar2"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().allValues("bam"), contains("A"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeaders() {
        var response = HttpResponse.builder()
            .statusCode(200)
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .build();

        assertThat(response.headers().allValues("foo"), contains("bar"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().allValues("bam"), contains("A"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeadersOnExisting() {
        var response = HttpResponse.builder()
            .statusCode(200)
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withAddedHeaders("a", "b")
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .build();

        assertThat(response.headers().allValues("foo"), contains("bar"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().allValues("bam"), contains("A"));
        assertThat(response.headers().allValues("a"), contains("b"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void addsHeadersToReplacements() {
        var response = HttpResponse.builder()
            .statusCode(200)
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .withAddedHeaders("a", "b", "foo", "bar2")
            .build();

        assertThat(response.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().allValues("bam"), contains("A"));
        assertThat(response.headers().allValues("a"), contains("b"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }
}
