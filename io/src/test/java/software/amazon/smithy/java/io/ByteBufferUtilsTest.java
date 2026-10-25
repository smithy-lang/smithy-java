/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

public class ByteBufferUtilsTest {

    @Test
    public void base64EncodingPreservesBufferPosition() {
        byte[] data = "prefix-hello-suffix".getBytes(StandardCharsets.UTF_8);
        byte[] expected = Base64.getEncoder()
                .encode("hello".getBytes(StandardCharsets.UTF_8));

        assertEncoding(expected, ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
        assertEncoding(expected, ByteBuffer.wrap(data, 7, 5));
        assertEncoding(expected, ByteBuffer.wrap(data, 7, 5).asReadOnlyBuffer());

        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).position(7).limit(12);
        assertEncoding(expected, direct);
    }

    private static void assertEncoding(byte[] expected, ByteBuffer buffer) {
        int position = buffer.position();
        assertThat(ByteBufferUtils.base64EncodeToBytes(buffer)).isEqualTo(expected);
        assertThat(buffer.position()).isEqualTo(position);
    }

    @Test
    public void base64EncodeToWritesAtOffsetForAllBufferShapes() {
        byte[] data = "prefix-hello-suffix".getBytes(StandardCharsets.UTF_8);
        byte[] expected = Base64.getEncoder()
                .encode("hello".getBytes(StandardCharsets.UTF_8));

        assertEncodeTo(expected, ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8)));
        assertEncodeTo(expected, ByteBuffer.wrap(data, 7, 5));
        assertEncodeTo(expected, ByteBuffer.wrap(data, 7, 5).asReadOnlyBuffer());

        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data).position(7).limit(12);
        assertEncodeTo(expected, direct);
    }

    @Test
    public void base64EncodedSizeMatchesJdkOutput() {
        for (int len = 0; len < 12; len++) {
            byte[] encoded = Base64.getEncoder().encode(new byte[len]);
            assertThat(ByteBufferUtils.base64EncodedSize(len)).isEqualTo(encoded.length);
        }
    }

    private static void assertEncodeTo(byte[] expected, ByteBuffer buffer) {
        int position = buffer.position();
        byte[] scratch = new byte[ByteBufferUtils.base64EncodedSize(buffer.remaining())];
        byte[] dst = new byte[expected.length + 3];
        dst[0] = '<';
        int written = ByteBufferUtils.base64EncodeTo(buffer, dst, 1, scratch);

        assertThat(written).isEqualTo(expected.length);
        byte[] region = new byte[written];
        System.arraycopy(dst, 1, region, 0, written);
        assertThat(region).isEqualTo(expected);
        assertThat(dst[0]).isEqualTo((byte) '<');
        assertThat(dst[written + 1]).isEqualTo((byte) 0);
        assertThat(buffer.position()).isEqualTo(position);
    }
}
