/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Interceptor for HTTP requests and responses.
 *
 * <p>Interceptors enable cross-cutting concerns such as:
 * <ul>
 *   <li>Logging and metrics</li>
 *   <li>Authentication and authorization</li>
 *   <li>Caching</li>
 *   <li>Retry logic</li>
 *   <li>Request/response transformation</li>
 *   <li>Redirect handling</li>
 * </ul>
 *
 * <h2>Execution Order</h2>
 *
 * <p>For a chain of interceptors [A, B, C]:
 * <ul>
 *   <li>{@link #beforeRequest} - forward order: A → B → C</li>
 *   <li>{@link #preemptRequest} - forward order: A → B → C (stops on first non-null)</li>
 *   <li>{@link #interceptResponse} - reverse order: C → B → A</li>
 *   <li>{@link #onError} - reverse order: C → B → A (stops on first non-null)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Interceptor implementations must be thread-safe. The same interceptor instance
 * may be called concurrently from multiple threads for different requests.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public class LoggingInterceptor implements HttpInterceptor {
 *     @Override
 *     public HttpRequest beforeRequest(HttpRequest request, Context context, HttpClient client) {
 *         System.out.println("Request: " + request.method() + " " + request.uri());
 *         return request;
 *     }
 *
 *     @Override
 *     public HttpResponse interceptResponse(HttpRequest request, Context context,
 *             HttpResponse response, HttpClient client) {
 *         System.out.println("Response: " + response.statusCode());
 *         return null; // Don't replace response
 *     }
 * }
 * }</pre>
 *
 * @see HttpClient.Builder#addInterceptor(HttpInterceptor)
 * @see RequestOptions.Builder#addInterceptor(HttpInterceptor)
 */
public interface HttpInterceptor {
    /**
     * Called before sending the request. Can modify the request.
     *
     * <p>Use this hook to add headers (authentication, tracing), modify URIs,
     * or transform the request body. This method cannot perform I/O operations
     * that might fail; use {@link #preemptRequest} for that.
     *
     * @param client the HTTP client (can be used to make additional requests)
     * @param request the outgoing request
     * @param context request-scoped context for passing data between interceptors
     * @return the modified request, or the original request unchanged
     */
    default HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) throws IOException {
        return request;
    }

    /**
     * Called to potentially handle the request without making a network call.
     *
     * <p>Use this hook to implement caching, mock responses for testing,
     * or short-circuit requests that can be handled locally.
     *
     * @param request the outgoing request
     * @param context request-scoped context for passing data between interceptors
     * @param client the HTTP client (can be used for cache validation requests)
     * @return a response to use instead of making a network call, or null to proceed normally
     * @throws IOException if an I/O error occurs while preparing the response
     */
    default HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) throws IOException {
        return null;
    }

    /**
     * Called after receiving the response status and headers.
     *
     * <p>Works for both {@link HttpClient#send} (buffered) and {@link HttpClient#exchange} (streaming):
     * <ul>
     *   <li><b>send():</b> Called immediately after network response is received</li>
     *   <li><b>exchange():</b> Called lazily when caller first accesses response</li>
     * </ul>
     *
     * <p>This hook can:
     * <ul>
     *   <li>Return null to keep the original response unchanged</li>
     *   <li>Return a different response to replace it (e.g., for retries)</li>
     *   <li>Call {@code client.send()} to retry the request</li>
     * </ul>
     *
     * <p><b>Warning for streaming exchanges:</b> When used with {@code exchange()},
     * the response body is a live stream. Reading the body will consume it, making
     * it unavailable to the caller. If you read the body, you must provide a
     * replacement response. Retrying is also dangerous since the request body
     * may have already been streamed.
     *
     * @param client the HTTP client (can be used to retry the request)
     * @param request the original request
     * @param context request-scoped context for passing data between interceptors
     * @param response the response received from the server (or previous interceptor)
     * @return a replacement response, or null to keep the current response
     * @throws IOException if an I/O error occurs while processing the response
     */
    default HttpResponse interceptResponse(
            HttpClient client,
            HttpRequest request,
            Context context,
            HttpResponse response
    ) throws IOException {
        return null;
    }

    /**
     * Called when an exception occurs during request execution.
     *
     * <p>Use this hook to implement fallback responses, retry logic with backoff,
     * or circuit breaker patterns. The exception has already occurred, so this
     * is a recovery mechanism.
     *
     * @param client the HTTP client (can be used to retry the request)
     * @param request the request that failed
     * @param context request-scoped context for passing data between interceptors
     * @param exception the exception that occurred during execution
     * @return a recovery response, or null to propagate the exception to the caller
     * @throws IOException if an I/O error occurs while attempting recovery
     */
    default HttpResponse onError(
            HttpClient client,
            HttpRequest request,
            Context context,
            IOException exception
    ) throws IOException {
        return null;
    }
}
