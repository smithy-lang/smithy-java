/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

public class SmithyHttpRequestImplTest {
    @Test
    public void addHeaders() throws Exception {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .withAddedHeader("foo", "bar   ")
                .withAddedHeader("Baz", "bam")
                .withAddedHeader("FOO", "bar2")
                .build();

        assertThat(request.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz"));
    }

    @Test
    public void addHeadersToExistingHeaders() throws Exception {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .withAddedHeader("foo", "bar   ")
                .withAddedHeader("Baz", "bam")
                .withAddedHeader("FOO", "bar2")
                .build();

        assertThat(request.headers().allValues("foo"), contains("bar0", "bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeaders() throws Exception {
        var request = HttpRequest.builder()
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
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .withAddedHeader("a", "b")
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
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .headers(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .withReplacedHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")))
                .withAddedHeader("a", "b")
                .withAddedHeader("foo", "bar2")
                .build();

        assertThat(request.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().allValues("a"), contains("b"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void bodyAutoAddsContentTypeAndLength() throws Exception {
        var body = DataStream.ofString("hello", "text/plain");
        var request = HttpRequest.builder()
                .method("POST")
                .uri(new URI("https://localhost"))
                .body(body)
                .build();

        assertEquals("5", request.headers().firstValue("content-length"));
        assertEquals("text/plain", request.headers().firstValue("content-type"));
    }

    @Test
    public void bodyHeadersCanBeOverridden() throws Exception {
        var body = DataStream.ofString("hello");
        var request = HttpRequest.builder()
                .method("POST")
                .uri(new URI("https://localhost"))
                .body(body)
                .withReplacedHeaders(Map.of("content-type", List.of("application/json")))
                .build();

        assertEquals("5", request.headers().firstValue("content-length"));
        assertEquals("application/json", request.headers().firstValue("content-type"));
    }

    @Test
    public void toUnmodifiableReturnsImmutable() throws Exception {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .build();

        assertSame(request, request.toUnmodifiable());
    }

    @Test
    public void toModifiableReturnsCopy() throws Exception {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .withAddedHeader("foo", "bar")
                .build();

        var modifiable = request.toModifiable();
        modifiable.headers().addHeader("foo", "baz");

        assertThat(request.headers().allValues("foo"), contains("bar"));
        assertThat(modifiable.headers().allValues("foo"), contains("bar", "baz"));
    }

    @Test
    public void modifiableCopyIsIndependent() throws Exception {
        var modifiable = HttpRequest.builder()
                .method("GET")
                .uri(new URI("https://localhost"))
                .withAddedHeader("foo", "bar")
                .buildModifiable();

        var copy = modifiable.copy();
        copy.headers().addHeader("foo", "baz");

        assertThat(modifiable.headers().allValues("foo"), contains("bar"));
        assertThat(copy.headers().allValues("foo"), contains("bar", "baz"));
        assertNotSame(modifiable, copy);
    }
}
