/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

/**
 * Thrown when an AWS shared config or credentials file cannot be parsed because it contains a
 * critical syntax error.
 */
public final class ConfigFileParseException extends RuntimeException {
    private final int lineNumber;

    ConfigFileParseException(int lineNumber, String message) {
        super(formatMessage(lineNumber, message));
        this.lineNumber = lineNumber;
    }

    /**
     * @return the 1-based line number the error was detected on, or {@code -1} if unknown.
     */
    public int lineNumber() {
        return lineNumber;
    }

    private static String formatMessage(int lineNumber, String message) {
        if (lineNumber > 0) {
            return "AWS config file parse error at line " + lineNumber + ": " + message;
        }
        return "AWS config file parse error: " + message;
    }
}
