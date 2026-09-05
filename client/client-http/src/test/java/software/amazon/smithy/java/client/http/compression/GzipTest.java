/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;

public class GzipTest {

    private static final Gzip GZIP = new Gzip();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 150, 3000, 15000})
    public void compressedBodyIsExactlyTheGzipStream(int payloadLength) throws Exception {
        var payload = payload(payloadLength);

        var compressed = GZIP.compress(DataStream.ofBytes(payload));
        var body = ByteBufferUtils.getBytes(compressed.asByteBuffer());

        assertThat(body, equalTo(referenceGzip(payload)));
        assertThat(compressed.contentLength(), equalTo((long) body.length));
        assertThat(decompress(body), equalTo(payload));
    }

    @Test
    public void preservesContentType() {
        var compressed = GZIP.compress(DataStream.ofString("{\"hello\":\"world\"}", "application/json"));

        assertThat(compressed.contentType(), equalTo("application/json"));
    }

    private static byte[] payload(int length) {
        var pattern = "Hello World! ";
        var sb = new StringBuilder(length);
        while (sb.length() < length) {
            sb.append(pattern, 0, Math.min(pattern.length(), length - sb.length()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] referenceGzip(byte[] payload) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(payload);
        }
        return out.toByteArray();
    }

    private static byte[] decompress(byte[] compressed) throws Exception {
        try (var gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzipIn.readAllBytes();
        }
    }
}
