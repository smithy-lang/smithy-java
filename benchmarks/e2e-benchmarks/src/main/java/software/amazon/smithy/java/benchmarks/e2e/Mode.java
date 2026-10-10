/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

enum Mode {
    /** In-process canned responses: the cross-SDK configuration. */
    STUB("stub"),
    /** smithy-java's HTTP client over TLS to a fixture server in a child JVM. */
    HTTPS("https");

    private final String label;

    Mode(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    static Mode parse(String value) {
        for (var mode : values()) {
            if (mode.label.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown mode '" + value + "'. Expected stub or https.");
    }
}
