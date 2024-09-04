/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core;

import java.nio.ByteBuffer;
import java.util.Base64;

public final class ByteBufferUtils {

    private ByteBufferUtils() {
    }

    public static String base64Encode(ByteBuffer buffer) {
        byte[] bytes;
        if (isExact(buffer)) {
            bytes = buffer.array();
        } else {
            bytes = new byte[buffer.remaining()];
            buffer.asReadOnlyBuffer().get(bytes);
        }
        return Base64.getEncoder().encodeToString(bytes);
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
