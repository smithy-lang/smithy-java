/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;

public class GzipCompressingInputStreamTest {

    @Test
    public void compressesDataCorrectly() throws Exception {
        var original = "Hello World!";
        var source = new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8));

        try (var gzipStream = new GzipCompressingInputStream(source)) {
            var compressed = gzipStream.readAllBytes();
            var decompressed = decompress(compressed);
            assertThat(decompressed, equalTo(original));
        }
    }

    @Test
    public void compressesLargeData() throws Exception {
        var original = "Hello World! ".repeat(10000);
        var source = new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8));

        try (var gzipStream = new GzipCompressingInputStream(source)) {
            var compressed = gzipStream.readAllBytes();
            assertThat(compressed.length, lessThan(original.length()));
            var decompressed = decompress(compressed);
            assertThat(decompressed, equalTo(original));
        }
    }

    @Test
    public void compressesEmptyData() throws Exception {
        var source = new ByteArrayInputStream(new byte[0]);

        try (var gzipStream = new GzipCompressingInputStream(source)) {
            var compressed = gzipStream.readAllBytes();
            var decompressed = decompress(compressed);
            assertThat(decompressed, equalTo(""));
        }
    }

    @Test
    public void readSingleByteWorks() throws Exception {
        var original = "AB";
        var source = new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8));

        try (var gzipStream = new GzipCompressingInputStream(source);
                var out = new ByteArrayOutputStream()) {
            int b;
            while ((b = gzipStream.read()) != -1) {
                out.write(b);
            }
            var decompressed = decompress(out.toByteArray());
            assertThat(decompressed, equalTo(original));
        }
    }

    @Test
    public void readWithBufferWorks() throws Exception {
        var original = "Test buffer read";
        var source = new ByteArrayInputStream(original.getBytes(StandardCharsets.UTF_8));

        try (var gzipStream = new GzipCompressingInputStream(source);
                var out = new ByteArrayOutputStream()) {
            var buffer = new byte[4];
            int len;
            while ((len = gzipStream.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, len);
            }
            var decompressed = decompress(out.toByteArray());
            assertThat(decompressed, equalTo(original));
        }
    }

    private String decompress(byte[] compressed) throws Exception {
        try (var gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed));
                var out = new ByteArrayOutputStream()) {
            var buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
