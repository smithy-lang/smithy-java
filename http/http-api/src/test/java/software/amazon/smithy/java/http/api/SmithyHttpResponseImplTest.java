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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.datastream.DataStream;

public class SmithyHttpResponseImplTest {
    @Test
    public void addHeaders() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .addHeader("foo", "bar   ")
                .addHeader("Baz", "bam")
                .addHeader("FOO", "bar2")
                .toUnmodifiable();

        assertThat(response.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz"));
    }

    @Test
    public void addHeadersToExistingHeaders() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .addHeader("foo", "bar   ")
                .addHeader("Baz", "bam")
                .addHeader("FOO", "bar2")
                .toUnmodifiable();

        assertThat(response.headers().allValues("foo"), contains("bar0", "bar", "bar2"));
        assertThat(response.headers().allValues("baz"), contains("bam"));
        assertThat(response.headers().allValues("bam"), contains("A"));
        assertThat(response.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeaders() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))));
        response.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = response.toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam"));
    }

    @Test
    public void replacesHeadersOnExisting() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))))
                .addHeader("a", "b");
        response.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = response.toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().allValues("a"), contains("b"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void addsHeadersToReplacements() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setHeaders(HttpHeaders.of(Map.of("foo", List.of("bar0"), "bam", List.of(" A "))));
        response.headers().placeHeaders(Map.of("foo", List.of("bar   "), "Baz", List.of("bam")));
        var result = response.addHeader("a", "b")
                .addHeader("foo", "bar2")
                .toUnmodifiable();

        assertThat(result.headers().allValues("foo"), contains("bar", "bar2"));
        assertThat(result.headers().allValues("baz"), contains("bam"));
        assertThat(result.headers().allValues("bam"), contains("A"));
        assertThat(result.headers().allValues("a"), contains("b"));
        assertThat(result.headers().map().keySet(), containsInAnyOrder("foo", "baz", "bam", "a"));
    }

    @Test
    public void bodyAutoAddsContentTypeAndLength() {
        var body = DataStream.ofString("hello", "text/plain");
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setBody(body)
                .toUnmodifiable();

        assertEquals("5", response.headers().firstValue("content-length"));
        assertEquals("text/plain", response.headers().firstValue("content-type"));
    }

    @Test
    public void bodyHeadersCanBeOverridden() {
        var body = DataStream.ofString("hello");
        var response = HttpResponse.create()
                .setStatusCode(200)
                .setBody(body)
                .setHeader("content-type", "application/json")
                .toUnmodifiable();

        assertEquals("5", response.headers().firstValue("content-length"));
        assertEquals("application/json", response.headers().firstValue("content-type"));
    }

    @Test
    public void toUnmodifiableReturnsImmutable() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .toUnmodifiable();

        assertSame(response, response.toUnmodifiable());
    }

    @Test
    public void toModifiableReturnsCopy() {
        var response = HttpResponse.create()
                .setStatusCode(200)
                .addHeader("foo", "bar")
                .toUnmodifiable();

        var modifiable = response.toModifiable();
        modifiable.headers().addHeader("foo", "baz");

        assertThat(response.headers().allValues("foo"), contains("bar"));
        assertThat(modifiable.headers().allValues("foo"), contains("bar", "baz"));
    }

    @Test
    public void modifiableCopyIsIndependent() {
        var modifiable = HttpResponse.create()
                .setStatusCode(200)
                .addHeader("foo", "bar")
                .toModifiable();

        var copy = modifiable.toModifiableCopy();
        copy.headers().addHeader("foo", "baz");

        assertThat(modifiable.headers().allValues("foo"), contains("bar"));
        assertThat(copy.headers().allValues("foo"), contains("bar", "baz"));
        assertNotSame(modifiable, copy);
    }
}
