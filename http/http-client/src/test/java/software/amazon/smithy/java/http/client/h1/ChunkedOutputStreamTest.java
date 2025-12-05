/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;

class ChunkedOutputStreamTest {

    @Test
    void writesSingleChunk() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write("hello".getBytes());
        stream.close();

        assertEquals("5\r\nhello\r\n0\r\n\r\n", delegate.toString());
    }

    @Test
    void writesMultipleChunks() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 5);
        stream.write("hello world".getBytes());
        stream.close();

        assertEquals("5\r\nhello\r\n5\r\n worl\r\n1\r\nd\r\n0\r\n\r\n", delegate.toString());
    }

    @Test
    void writesEmptyBody() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate);
        stream.close();

        assertEquals("0\r\n\r\n", delegate.toString());
    }

    @Test
    void writesSingleBytes() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write('a');
        stream.write('b');
        stream.write('c');
        stream.close();

        assertEquals("3\r\nabc\r\n0\r\n\r\n", delegate.toString());
    }

    @Test
    void flushWritesChunk() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write("hello".getBytes());
        stream.flush();

        assertEquals("5\r\nhello\r\n", delegate.toString());
    }

    @Test
    void writesTrailers() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write("hello".getBytes());
        stream.setTrailers(HttpHeaders.of(Map.of("x-checksum", List.of("abc123"))));
        stream.close();

        assertEquals("5\r\nhello\r\n0\r\nx-checksum: abc123\r\n\r\n", delegate.toString());
    }

    @Test
    void writesMultipleTrailers() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write("hi".getBytes());
        stream.setTrailers(HttpHeaders.of(Map.of(
                "x-foo",
                List.of("bar"),
                "x-baz",
                List.of("qux"))));
        stream.close();

        String result = delegate.toString();
        assertTrue(result.startsWith("2\r\nhi\r\n0\r\n"));
        assertTrue(result.contains("x-foo: bar\r\n"));
        assertTrue(result.contains("x-baz: qux\r\n"));
        assertTrue(result.endsWith("\r\n\r\n"));
    }

    @Test
    void writesMultiValueTrailer() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 1024);
        stream.write("hi".getBytes());
        stream.setTrailers(HttpHeaders.of(Map.of("x-multi", List.of("a", "b"))));
        stream.close();

        String result = delegate.toString();
        assertTrue(result.contains("x-multi: a\r\n"));
        assertTrue(result.contains("x-multi: b\r\n"));
    }

    @Test
    void singleByteWriteFlushesWhenBufferFull() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate, 3);
        stream.write('a');
        stream.write('b');
        stream.write('c'); // buffer full, triggers flush

        assertEquals("3\r\nabc\r\n", delegate.toString());
    }

    @Test
    void closeIsIdempotent() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate);
        stream.write("hi".getBytes());
        stream.close();
        stream.close();

        assertEquals("2\r\nhi\r\n0\r\n\r\n", delegate.toString());
    }

    @Test
    void throwsAfterClose() throws IOException {
        var delegate = new ByteArrayOutputStream();
        var stream = new ChunkedOutputStream(delegate);
        stream.close();

        assertThrows(IOException.class, () -> stream.write(1));
    }

    @Test
    void throwsOnInvalidChunkSize() {
        var delegate = new ByteArrayOutputStream();

        assertThrows(IllegalArgumentException.class, () -> new ChunkedOutputStream(delegate, 0));
    }

    @Test
    void throwsOnNullDelegate() {
        assertThrows(NullPointerException.class, () -> new ChunkedOutputStream(null));
    }
}
