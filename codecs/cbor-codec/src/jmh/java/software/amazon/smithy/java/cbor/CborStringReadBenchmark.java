/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.charset.StandardCharsets;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class CborStringReadBenchmark {

    private static final int OFFSET = 3;

    @Param({
            "ascii_4",
            "ascii_8",
            "ascii_16",
            "ascii_32",
            "ascii_128",
            "ascii_1024",
            "latin1_mixed_128",
            "utf16_128",
    })
    public String testCaseId;

    private byte[] buffer;
    private int length;

    @Setup
    public void setup() {
        byte[] utf8 = StringWriteTestCases.value(testCaseId).getBytes(StandardCharsets.UTF_8);
        buffer = new byte[utf8.length + 2 * OFFSET];
        System.arraycopy(utf8, 0, buffer, OFFSET, utf8.length);
        length = utf8.length;
    }

    @Benchmark
    public String readTextString() {
        return CborReadUtil.readTextString(buffer, OFFSET, length);
    }
}
