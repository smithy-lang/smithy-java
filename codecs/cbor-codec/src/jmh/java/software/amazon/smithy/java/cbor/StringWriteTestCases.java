/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

final class StringWriteTestCases {

    private StringWriteTestCases() {}

    static String value(String testCaseId) {
        int separator = testCaseId.lastIndexOf('_');
        int length = Integer.parseInt(testCaseId.substring(separator + 1));
        return switch (testCaseId.substring(0, separator)) {
            case "ascii" -> ascii(length);
            case "latin1" -> "é".repeat(length);
            case "latin1_mixed" -> "café ".repeat((length + 4) / 5).substring(0, length);
            case "latin1_tail" -> ascii(length - 1) + 'é';
            case "utf16" -> "€".repeat(length);
            default -> throw new IllegalArgumentException("Unknown test case: " + testCaseId);
        };
    }

    private static String ascii(int length) {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        return alphabet.repeat((length + alphabet.length() - 1) / alphabet.length()).substring(0, length);
    }
}
