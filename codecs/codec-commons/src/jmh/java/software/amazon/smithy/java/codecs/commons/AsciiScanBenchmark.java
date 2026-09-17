/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class AsciiScanBenchmark {

    private static final VarHandle LONG_HANDLE =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    @Param({"4", "8", "16", "32", "64", "128", "512", "2048"})
    public int length;

    private byte[] ascii;

    @Setup
    public void setup() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        ascii = alphabet.repeat((length + alphabet.length() - 1) / alphabet.length())
                .substring(0, length)
                .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!CompactStringAccess.isCountPositivesIntrinsic()) {
            throw new IllegalStateException("countPositives intrinsic not available");
        }
    }

    @Benchmark
    public int scalar() {
        byte[] b = ascii;
        for (int i = 0; i < b.length; i++) {
            if (b[i] < 0) {
                return i;
            }
        }
        return b.length;
    }

    @Benchmark
    public int swar() {
        byte[] b = ascii;
        int len = b.length;
        int i = 0;
        for (; i + 8 <= len; i += 8) {
            long word = (long) LONG_HANDLE.get(b, i);
            if ((word & 0x8080808080808080L) != 0) {
                return i;
            }
        }
        for (; i < len; i++) {
            if (b[i] < 0) {
                return i;
            }
        }
        return len;
    }

    @Benchmark
    public int countPositives() {
        return CompactStringAccess.countPositives(ascii, 0, ascii.length);
    }
}
