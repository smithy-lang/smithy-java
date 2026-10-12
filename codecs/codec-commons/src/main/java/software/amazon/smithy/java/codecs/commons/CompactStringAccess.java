/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class CompactStringAccess {
    public static final String DISABLE_PROPERTY =
            "software.amazon.smithy.java.codecs.commons.disableCompactStringAccess";

    private static final MethodType COUNT_POSITIVES_TYPE =
            MethodType.methodType(int.class, byte[].class, int.class, int.class);
    private static final MethodHandles.Lookup TRUSTED_LOOKUP = trustedLookup();
    private static final Access ACCESS = initializeAccess();
    // Keep the handle constant so C2 can inline the intrinsic.
    private static final MethodHandle COUNT_POSITIVES = jdkCountPositives();
    private static final boolean COMPACT_STRINGS = detectCompactStrings();

    private CompactStringAccess() {}

    public static boolean isAvailable() {
        return ACCESS != null;
    }

    public static boolean isCountPositivesIntrinsic() {
        return COUNT_POSITIVES != null;
    }

    static boolean isCompactStrings() {
        return COMPACT_STRINGS;
    }

    public static byte[] latin1Bytes(String value) {
        Access access = ACCESS;
        if (access == null || (byte) access.coder.get(value) != 0) {
            return null;
        }
        return (byte[]) access.value.get(value);
    }

    /**
     * Decodes bytes the caller has verified are ASCII.
     *
     * <p>The hibyte constructor avoids charset dispatch, but needs the charset path for vectorized inflation under
     * {@code -XX:-CompactStrings}.
     */
    @SuppressWarnings("deprecation")
    public static String asciiString(byte[] bytes, int off, int len) {
        if (COMPACT_STRINGS) {
            return new String(bytes, 0, off, len);
        }
        return new String(bytes, off, len, StandardCharsets.ISO_8859_1);
    }

    /**
     * Counts a safe prefix of non-negative bytes. Returns {@code len} when the whole range is non-negative, but may
     * stop before the first negative byte.
     */
    public static int countPositives(byte[] bytes, int off, int len) {
        MethodHandle handle = COUNT_POSITIVES;
        if (handle != null) {
            try {
                return (int) handle.invokeExact(bytes, off, len);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }
        int limit = off + len;
        for (int i = off; i < limit; i++) {
            if (bytes[i] < 0) {
                return i - off;
            }
        }
        return len;
    }

    private static MethodHandles.Lookup trustedLookup() {
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
            return (MethodHandles.Lookup) unsafeClass
                    .getMethod("getObject", Object.class, long.class)
                    .invoke(unsafe, base, offset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Access initializeAccess() {
        if (TRUSTED_LOOKUP == null) {
            return null;
        }
        try {
            return new Access(
                    TRUSTED_LOOKUP.findVarHandle(String.class, "value", byte[].class),
                    TRUSTED_LOOKUP.findVarHandle(String.class, "coder", byte.class));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean detectCompactStrings() {
        Access access = ACCESS;
        // Without internals access, assume the JDK default.
        return access == null || (byte) access.coder.get("a") == 0;
    }

    private static MethodHandle jdkCountPositives() {
        if (TRUSTED_LOOKUP == null) {
            return null;
        }
        try {
            return TRUSTED_LOOKUP.findStatic(
                    Class.forName("java.lang.StringCoding"),
                    "countPositives",
                    COUNT_POSITIVES_TYPE);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record Access(VarHandle value, VarHandle coder) {}
}
