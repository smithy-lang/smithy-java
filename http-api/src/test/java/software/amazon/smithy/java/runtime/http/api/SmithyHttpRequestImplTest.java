/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SmithyHttpRequestImplTest {
    @Test
    public void addHeaders() throws Exception {
        var request = SmithyHttpRequest.builder()
            .method("GET")
            .uri(new URI("https://localhost"))
            .withAddedHeaders("foo", "bar   ", "Baz", "bam", "FOO", "bar2")
            .build();

        assertThat(request.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz"));
    }

    @Test
    public void addHeadersToExistingHeaders() throws Exception {
        var request = SmithyHttpRequest.builder()
            .method("GET")
            .uri(new URI("https://localhost"))
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withAddedHeaders("foo", "bar   ", "Baz", "bam", "FOO", "bar2")
            .build();

        assertThat(request.headers().allValues("foo"), contains("bar0", "bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeaders() throws Exception {
        var request = SmithyHttpRequest.builder()
            .method("GET")
            .uri(new URI("https://localhost"))
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .build();

        assertThat(request.headers().allValues("foo"), contains("bar"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeadersOnExisting() throws Exception {
        var request = SmithyHttpRequest.builder()
            .method("GET")
            .uri(new URI("https://localhost"))
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withAddedHeaders("a", "b")
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .build();

        assertThat(request.headers().allValues("foo"), contains("bar"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().allValues("a"), contains("b"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void addsHeadersToReplacements() throws Exception {
        var request = SmithyHttpRequest.builder()
            .method("GET")
            .uri(new URI("https://localhost"))
            .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
            .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
            .withAddedHeaders("a", "b", "foo", "bar2")
            .build();

        assertThat(request.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().allValues("a"), contains("b"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }
}
