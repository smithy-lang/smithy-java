/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.util.Objects;

/**
 * Route key for connection pooling: scheme + host + port.
 */
record Route(String scheme, String host, int port) {
    Route {
        Objects.requireNonNull(scheme);
        Objects.requireNonNull(host);
    }

    boolean isTls() {
        return "https".equalsIgnoreCase(scheme);
    }
}
