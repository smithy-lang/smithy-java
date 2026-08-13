/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

final class CompactStringAccess {
    private static final VarHandle VALUE;

    static {
        VarHandle value = null;
        try {
            Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            implLookup.setAccessible(true);
            MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup) implLookup.get(null);
            value = trustedLookup.findVarHandle(String.class, "value", byte[].class);
        } catch (Throwable ignored) {
            // The accelerator is opportunistic. Supported String APIs remain the fallback.
        }
        VALUE = value;
    }

    private CompactStringAccess() {}

    /**
     * Returns the backing array of a Latin-1 {@code String}, one byte per character, or null when
     * the string is UTF-16 encoded or the accelerator is unavailable.
     *
     * <p>The encoding is derived from the array length rather than read from {@code String.coder}:
     * a Latin-1 string stores one byte per character, a UTF-16 string two, and the two only agree
     * at length zero, where an empty array is the correct answer either way. That holds under
     * {@code -XX:-CompactStrings} as well, where every string is UTF-16. Deriving it saves a
     * second {@code VarHandle} read on the hottest string path.
     */
    static byte[] latin1Bytes(String value) {
        if (VALUE == null) {
            return null;
        }
        byte[] bytes = (byte[]) VALUE.get(value);
        return bytes.length == value.length() ? bytes : null;
    }
}
