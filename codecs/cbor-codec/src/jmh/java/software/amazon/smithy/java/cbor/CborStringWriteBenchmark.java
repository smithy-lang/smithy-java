/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class CborStringWriteBenchmark {

    @Param({
            "ascii_4",
            "ascii_8",
            "ascii_16",
            "ascii_32",
            "ascii_64",
            "ascii_128",
            "ascii_1024",
            "latin1_128",
            "latin1_mixed_128",
            "latin1_tail_128",
            "utf16_128",
    })
    public String testCaseId;

    private String value;
    private CborSerializer serializer;

    @Setup
    public void setup() {
        value = StringWriteTestCases.value(testCaseId);
        serializer = new CborSerializer();
    }

    @Benchmark
    public int writeString() {
        CborSerializer s = serializer;
        s.pos = 0;
        s.writeString(null, value);
        return s.pos + s.buf[s.pos - 1];
    }
}
