/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.time.Duration;

/**
 * Per-request configuration options for HTTP requests.
 *
 * @param requestTimeout Per-request timeout override, or null to use client default.
 */
public record RequestOptions(Duration requestTimeout) {
    private static final RequestOptions DEFAULTS = new RequestOptions(null);

    public RequestOptions {
        if (requestTimeout != null && (requestTimeout.isNegative() || requestTimeout.isZero())) {
            throw new IllegalArgumentException("requestTimeout must be positive or null: " + requestTimeout);
        }
    }

    /**
     * Returns default request options.
     *
     * @return default request options
     */
    public static RequestOptions defaults() {
        return DEFAULTS;
    }
}
