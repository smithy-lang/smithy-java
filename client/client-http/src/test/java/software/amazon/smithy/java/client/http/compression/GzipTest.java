/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 150, 3000, 15000})
    public void decompressesTheGzipStream(int payloadLength) throws Exception {
        var payload = payload(payloadLength);

        var decompressed = GZIP.decompress(DataStream.ofBytes(referenceGzip(payload), "application/json"));

        assertThat(ByteBufferUtils.getBytes(decompressed.asByteBuffer()), equalTo(payload));
        assertThat(decompressed.contentType(), equalTo("application/json"));
        assertThat(decompressed.hasKnownLength(), equalTo(false));
    }

    @Test
    public void decompressRejectsDataThatIsNotGzip() {
        var decompressed = GZIP.decompress(DataStream.ofString("not gzip"));

        var e = assertThrows(UncheckedIOException.class, decompressed::asByteBuffer);
        assertThat(e.getCause() instanceof ZipException, equalTo(true));
    }

    @Test
    public void decompressesEmptyDataToEmptyData() {
        var decompressed = GZIP.decompress(DataStream.ofBytes(new byte[0]));

        assertThat(ByteBufferUtils.getBytes(decompressed.asByteBuffer()).length, equalTo(0));
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
