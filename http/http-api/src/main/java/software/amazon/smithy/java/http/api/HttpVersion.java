/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

/**
 * Enumeration of the HTTP protocol versions.
 */
public enum HttpVersion {
    HTTP_1_1,
    HTTP_2;

    /**
     * Returns the enum value that the version represents.
     *
     * @param version The string to convert to enum value
     * @return The enum value that the version represents.
     */
    public static HttpVersion from(String version) {
        return switch (version) {
            case "HTTP/1.1" -> HTTP_1_1;
            case "HTTP/2.0" -> HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    @Override
    public String toString() {
        return switch (this) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
        };
    }
}
