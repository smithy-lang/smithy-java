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
import software.amazon.smithy.java.io.datastream.DataStream;

public class GzipTest {

    private final Gzip gzip = new Gzip();

    @Test
    public void algorithmIdReturnsGzip() {
        assertThat(gzip.algorithmId(), equalTo("gzip"));
    }

    @Test
    public void compressesKnownLengthData() throws Exception {
        // Use larger, repetitive data that compresses well
        String original = "Hello World! ".repeat(10);
        DataStream input = DataStream.ofString(original);

        DataStream compressed = gzip.compress(input);

        // Verify compressed data is smaller
        assertThat(compressed.contentLength(), lessThan((long) original.length()));

        // Verify decompression produces original
        String decompressed = decompress(compressed.asByteBuffer().array());
        assertThat(decompressed, equalTo(original));
    }

    @Test
    public void compressesLargeStreamInChunks() throws Exception {
        // Create 100KB of data
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("0123456789");
        }
        String original = sb.toString();
        byte[] bytes = original.getBytes(StandardCharsets.UTF_8);
        DataStream input = DataStream.ofInputStream(new ByteArrayInputStream(bytes));

        DataStream compressed = gzip.compress(input);

        // Verify decompression produces original
        byte[] compressedBytes = compressed.asInputStream().readAllBytes();
        String decompressed = decompress(compressedBytes);
        assertThat(decompressed, equalTo(original));
    }

    @Test
    public void compressesEmptyData() throws Exception {
        DataStream input = DataStream.ofString("");

        DataStream compressed = gzip.compress(input);

        String decompressed = decompress(compressed.asByteBuffer().array());
        assertThat(decompressed, equalTo(""));
    }

    private String decompress(byte[] compressed) throws Exception {
        try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
