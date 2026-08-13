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
    private static final VarHandle CODER;

    static {
        VarHandle value = null;
        VarHandle coder = null;
        try {
            Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            implLookup.setAccessible(true);
            MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup) implLookup.get(null);
            value = trustedLookup.findVarHandle(String.class, "value", byte[].class);
            coder = trustedLookup.findVarHandle(String.class, "coder", byte.class);
        } catch (Throwable ignored) {
            // The accelerator is opportunistic. Supported String APIs remain the fallback.
        }
        VALUE = value;
        CODER = coder;
    }

    private CompactStringAccess() {}

    static byte[] latin1Bytes(String value) {
        if (VALUE == null || (byte) CODER.get(value) != 0) {
            return null;
        }
        return (byte[]) VALUE.get(value);
    }
}
