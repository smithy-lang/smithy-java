/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class JsonStringWriteBenchmark {

    @Param({
            "ascii_0",
            "ascii_3",
            "ascii_4",
            "ascii_7",
            "ascii_8",
            "ascii_16",
            "ascii_24",
            "ascii_25",
            "ascii_128",
            "latin1_128",
            "utf16_128",
            "escaped_128",
    })
    public String testCaseId;

    private String value;
    private byte[] buffer;

    @Setup
    public void setup() {
        value = switch (testCaseId) {
            case "ascii_0" -> "";
            case "ascii_3" -> ascii(3);
            case "ascii_4" -> ascii(4);
            case "ascii_7" -> ascii(7);
            case "ascii_8" -> ascii(8);
            case "ascii_16" -> ascii(16);
            case "ascii_24" -> ascii(24);
            case "ascii_25" -> ascii(25);
            case "ascii_128" -> ascii(128);
            case "latin1_128" -> "\u00e9".repeat(128);
            case "utf16_128" -> "\u20ac".repeat(128);
            case "escaped_128" -> ascii(127) + '"';
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };
        buffer = new byte[JsonWriteUtils.maxQuotedStringBytes(value)];
    }

    @Benchmark
    public int writeWithSmithyJson() {
        int end = JsonWriteUtils.writeQuotedString(buffer, 0, value);
        return end + buffer[end - 1];
    }

    private static String ascii(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return alphabet.repeat((length + alphabet.length() - 1) / alphabet.length()).substring(0, length);
    }
}
