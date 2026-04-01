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
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .addHeader("foo", "bar   ")
                .addHeader("Baz", "bam")
                .addHeader("FOO", "bar2")
                .toUnmodifiable();

        assertThat(request.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz"));
    }

    @Test
    public void addHeadersToExistingHeaders() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .addHeader("foo", "bar   ")
                .addHeader("Baz", "bam")
                .addHeader("FOO", "bar2")
                .toUnmodifiable();

        assertThat(request.headers().allValues("foo"), contains("bar0", "bar", "bar2"));
        assertThat(request.headers().allValues("baz"), contains("bam"));
        assertThat(request.headers().allValues("bam"), contains("A"));
        assertThat(request.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeaders() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))));
        request.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = request.toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeadersOnExisting() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .addHeader("a", "b");
        request.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = request.toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().allValues("a"), contains("b"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void addsHeadersToReplacements() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))));
        request.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = request.addHeader("a", "b")
                .addHeader("foo", "bar2")
                .toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().allValues("a"), contains("b"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void bodyAutoAddsContentTypeAndLength() throws Exception {
        var body = DataStream.ofString("hello", "text/plain");
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(new URI("https://localhost"))
                .setBody(body)
                .toUnmodifiable();

        assertEquals("5", request.headers().firstValue("content-length"));
        assertEquals("text/plain", request.headers().firstValue("content-type"));
    }

    @Test
    public void bodyHeadersCanBeOverridden() throws Exception {
        var body = DataStream.ofString("hello");
        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(new URI("https://localhost"))
                .setBody(body)
                .setHeader("content-type", "application/json")
                .toUnmodifiable();

        assertEquals("5", request.headers().firstValue("content-length"));
        assertEquals("application/json", request.headers().firstValue("content-type"));
    }

    @Test
    public void toUnmodifiableReturnsImmutable() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .toUnmodifiable();

        assertSame(request, request.toUnmodifiable());
    }

    @Test
    public void toModifiableReturnsCopy() throws Exception {
        var request = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .addHeader("foo", "bar")
                .toUnmodifiable();

        var modifiable = request.toModifiable();
        modifiable.headers().addHeader("foo", "baz");

        assertThat(request.headers().allValues("foo"), contains("bar"));
        assertThat(modifiable.headers().allValues("foo"), contains("bar", "baz"));
    }

    @Test
    public void modifiableCopyIsIndependent() throws Exception {
        var modifiable = HttpRequest.create()
                .setMethod("GET")
                .setUri(new URI("https://localhost"))
                .addHeader("foo", "bar")
                .toModifiable();

        var copy = modifiable.toModifiableCopy();
        copy.headers().addHeader("foo", "baz");

        assertThat(modifiable.headers().allValues("foo"), contains("bar"));
        assertThat(copy.headers().allValues("foo"), contains("bar", "baz"));
        assertNotSame(modifiable, copy);
    }
}
