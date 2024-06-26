/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ByteBufferUtils {

    private ByteBufferUtils() {
    }

    public static String base64Encode(ByteBuffer buffer) {
        if (isExact(buffer)) {
            return Base64.getEncoder().encodeToString(buffer.array());
        }
        return StandardCharsets.UTF_8.decode(Base64.getEncoder().encode(buffer.asReadOnlyBuffer())).toString();
    }

    public static byte[] getBytes(ByteBuffer buffer) {
        if (isExact(buffer)) {
            return buffer.array();
        }
        byte[] bytes = new byte[buffer.remaining()];
        buffer.asReadOnlyBuffer().get(bytes);
        return bytes;
    }

    private static boolean isExact(ByteBuffer buffer) {
        return buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.remaining() == buffer.array().length;
    }
}
