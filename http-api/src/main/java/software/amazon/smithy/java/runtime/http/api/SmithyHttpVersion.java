/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

public enum SmithyHttpVersion {
    HTTP_1_1,
    HTTP_2;


    @Override
    public String toString() {
        return switch (this) {
            case HTTP_1_1 -> "HTTP/1.1";
            case HTTP_2 -> "HTTP/2.0";
        };
    }
}
