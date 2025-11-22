/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Interceptor for HTTP requests and responses, used to implement caching, logging, challenges, etc.
 *
 * <p>Interceptors are called in order for requests, and in reverse order for responses.
 */
public interface HttpInterceptor {
    /**
     * Called before sending the request. Can modify the request.
     *
     * @param request the outgoing request
     * @param client the client (can be used to make additional requests)
     * @return modified request, or the original request
     */
    default HttpRequest beforeRequest(HttpRequest request, HttpClient client) {
        return request;
    }

    /**
     * Called to potentially handle the request without hitting the network.
     *
     * @param request the request
     * @param client the client (can be used to make requests for cache validation, etc.)
     * @return a response if this interceptor handles it, or null to continue
     */
    default HttpResponse handleRequest(HttpRequest request, HttpClient client) throws IOException {
        return null;
    }

    /**
     * Observe the response after receiving.
     * Works with both send() and exchange().
     *
     * <p>This is a hook for logging, metrics, caching, etc.
     * You cannot modify or replace the response - just observe it.
     */
    default void afterResponse(HttpRequest request, HttpResponse response) throws IOException {
        // Hook for observation only
    }

    /**
     * Called when an exception occurs during request execution.
     *
     * @param request the request that failed
     * @param exception the exception that occurred
     * @param client the client (can be used to retry)
     * @return a response to recover, or null to propagate the exception
     */
    default HttpResponse onError(HttpRequest request, IOException exception, HttpClient client) throws IOException {
        return null;
    }
}
