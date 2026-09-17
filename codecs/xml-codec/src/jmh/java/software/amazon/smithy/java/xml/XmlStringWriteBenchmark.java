/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class XmlStringWriteBenchmark {

    @Param({
            "ascii_8",
            "ascii_32",
            "ascii_128",
            "ascii_1024",
            "escaped_128",
            "latin1_128",
            "latin1_mixed_128",
            "latin1_tail_128",
            "utf16_128",
    })
    public String testCaseId;

    private String value;
    private byte[] buffer;

    @Setup
    public void setup() {
        int separator = testCaseId.lastIndexOf('_');
        int length = Integer.parseInt(testCaseId.substring(separator + 1));
        value = switch (testCaseId.substring(0, separator)) {
            case "ascii" -> ascii(length);
            case "escaped" -> ascii(length - 1) + '<';
            case "latin1" -> "é".repeat(length);
            case "latin1_mixed" -> "café ".repeat((length + 4) / 5).substring(0, length);
            case "latin1_tail" -> ascii(length - 1) + 'é';
            case "utf16" -> "€".repeat(length);
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };
        buffer = new byte[XmlWriteUtils.maxEscapedAttributeBytes(value)];
    }

    @Benchmark
    public int writeEscapedText() {
        int end = XmlWriteUtils.writeEscapedText(buffer, 0, value);
        return end + buffer[end - 1];
    }

    @Benchmark
    public int writeEscapedAttribute() {
        int end = XmlWriteUtils.writeEscapedAttribute(buffer, 0, value);
        return end + buffer[end - 1];
    }

    private static String ascii(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return alphabet.repeat((length + alphabet.length() - 1) / alphabet.length()).substring(0, length);
    }
}
