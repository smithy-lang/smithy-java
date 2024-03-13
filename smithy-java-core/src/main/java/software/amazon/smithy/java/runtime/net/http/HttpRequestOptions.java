/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.net.http;

import java.time.Duration;
import software.amazon.smithy.java.runtime.util.Constant;

/**
 * Constants used to define HTTP client settings.
 */
public final class HttpRequestOptions {
    /**
     * The time from when an HTTP request is sent, and when the response is received. If the response is not
     * received in time, then the request is considered timed out. This setting does not apply to streaming
     * operations.
     */
    public static final Constant<Duration> REQUEST_TIMEOUT = new Constant<>(Duration.class, "HTTP.RequestTimeout");

    private HttpRequestOptions() {}
}
