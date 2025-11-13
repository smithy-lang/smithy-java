/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * HTTP request / response message exchange.
 */
public final class HttpMessageExchange implements MessageExchange<HttpRequest, HttpResponse> {
    /**
     * Static instance of the message exchange.
     */
    public static final HttpMessageExchange INSTANCE = new HttpMessageExchange();

    private HttpMessageExchange() {}
}
