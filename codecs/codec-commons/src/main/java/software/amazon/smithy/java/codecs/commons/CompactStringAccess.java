/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CompactStringAccess {
    public static final String DISABLE_PROPERTY =
            "software.amazon.smithy.java.codecs.commons.disableCompactStringAccess";

    private static final Access ACCESS = initialize();

    private CompactStringAccess() {}

    public static boolean isAvailable() {
        return ACCESS != null;
    }

    public static byte[] latin1Bytes(String value) {
        Access access = ACCESS;
        if (access == null || (byte) access.coder.get(value) != 0) {
            return null;
        }
        return (byte[]) access.value.get(value);
    }

    private static Access initialize() {
        if (Boolean.getBoolean(DISABLE_PROPERTY)
                || System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            return null;
        }

        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);

            Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object base = unsafeClass
                    .getMethod("staticFieldBase", Field.class)
                    .invoke(unsafe, implLookup);
            long offset = (long) unsafeClass
                    .getMethod("staticFieldOffset", Field.class)
                    .invoke(unsafe, implLookup);
            MethodHandles.Lookup trustedLookup =
                    (MethodHandles.Lookup) unsafeClass
                            .getMethod("getObject", Object.class, long.class)
                            .invoke(unsafe, base, offset);

            return new Access(
                    trustedLookup.findVarHandle(String.class, "value", byte[].class),
                    trustedLookup.findVarHandle(String.class, "coder", byte.class));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record Access(VarHandle value, VarHandle coder) {}
}
