/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

/**
 * How the generated client reaches the response fixture.
 *
 * <p>{@code stub} is the cross-SDK configuration: the transport is replaced in-process and no bytes leave the JVM.
 * {@code http} and {@code https} drive smithy-java's own HTTP client, TLS included, against a fixture server that
 * returns the same response bytes, so the full client path can be compared with the stub on identical operations.
 */
enum TransportMode {
    STUB("stub"),
    HTTP("http"),
    HTTPS("https");

    private final String label;

    TransportMode(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    boolean isNetwork() {
        return this != STUB;
    }

    String defaultEndpoint() {
        return switch (this) {
            case STUB -> BenchmarkProtocol.ENDPOINT;
            case HTTP -> "http://127.0.0.1:8080";
            case HTTPS -> "https://127.0.0.1:8443";
        };
    }

    static TransportMode parse(String value) {
        for (var mode : values()) {
            if (mode.label.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown transport '" + value + "'. Expected stub, http or https.");
    }
}
