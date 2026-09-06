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
}
