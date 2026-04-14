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
 * <p>Interceptors enable cross-cutting concerns such as logging, metrics, redirects, authentication, caching, retries,
 * request/response pre-flight transformations, etc.
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
 * <h2>Execution Flow</h2>
 *
 * <p>The following diagram shows the execution flow for a request:
 *
 * <pre>
 *   beforeRequest (A → B → C)
 *          │
 *          ▼
 *   preemptRequest (A → B → C) ──── returns response? ────┐
 *          │                                              │
 *          │ null                                         │
 *          ▼                                              │
 *   ┌─────────────────┐                                   │
 *   │ Network Request │                                   │
 *   └────────┬────────┘                                   │
 *            │                                            │
 *            ▼                                            ▼
 *   interceptResponse (C → B → A) ◄───────────────────────┘
 *            │
 *            │ throws IOException?
 *            ▼
 *   onError (C → B → A) ──── returns recovery? ──── return recovery
 *            │
 *            │ null
 *            ▼
 *      propagate exception
 * </pre>
 *
 * <h2>Error Handling</h2>
 *
 * <p>If any interceptor throws an {@link IOException}:
 * <ul>
 *   <li>From {@link #beforeRequest} or {@link #preemptRequest}: propagates directly to caller</li>
 *   <li>From network request: passed to {@link #onError} for recovery</li>
 *   <li>From {@link #interceptResponse}: passed to {@link #onError} for recovery</li>
 *   <li>From {@link #onError}: propagates directly to caller</li>
 * </ul>
 *
 * <p>This allows interceptors that perform retries in {@link #interceptResponse} to have their
 * failures handled by error recovery interceptors.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>Interceptor implementations must be thread-safe. The same interceptor instance may be called concurrently
 * from multiple threads for different requests. However, for a single request, all callbacks are invoked sequentially
 * on the same thread. This means request-scoped state stored in the {@link Context} can be accessed without
 * synchronization.
 *
 * <p>Interceptors may block freely in any callback method. No locks are held when interceptors are invoked, so
 * blocking will not cause deadlocks or contention with other requests.
 *
 * <h2>Example</h2>
 *
 * {@snippet :
 * public class LoggingInterceptor implements HttpInterceptor {
 *     @Override
 *     public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
 *         System.out.println("Request: " + request.method() + " " + request.uri());
 *         return request;
 *     }
 *
 *     @Override
 *     public HttpResponse interceptResponse(HttpClient client, HttpRequest request,
 *             Context context, HttpResponse response) {
 *         System.out.println("Response: " + response.statusCode());
 *         return response;
 *     }
 * }
 * }
 *
 * @see HttpClient.Builder#addInterceptor(HttpInterceptor)
 * @see RequestOptions.Builder#addInterceptor(HttpInterceptor)
 */
public interface HttpInterceptor {
    /**
     * Called before sending the request and can modify the request pre-flight.
     *
     * <p>Use this hook to add headers (authentication, tracing), modify URIs, or transform the request body.
     *
     * <p>Errors thrown from this method propagate directly to the caller without passing through {@link #onError}.
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
     * <p>Use this hook to implement caching, mock responses for testing, or short-circuit requests that can be
     * handled locally.
     *
     * <p>Errors thrown from this method propagate directly to the caller without passing through {@link #onError}.
     *
     * @param client the HTTP client (can be used for cache validation requests)
     * @param request the outgoing request
     * @param context request-scoped context for passing data between interceptors
     * @return a response to use instead of making a network call, or null to proceed normally
     * @throws IOException if an I/O error occurs (propagates directly to caller)
     */
    default HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) throws IOException {
        return null;
    }

    /**
     * Called after receiving the response status and headers.
     *
     * <p>Works for both {@link HttpClient#send} (buffered) and {@link HttpClient#newExchange} (streaming):
     * <ul>
     *   <li><b>send():</b> Called immediately after network response is received</li>
     *   <li><b>exchange():</b> Called lazily when caller first accesses response</li>
     * </ul>
     *
     * <p>This hook can:
     * <ul>
     *   <li>Return the given response to keep the original response unchanged</li>
     *   <li>Return a different response to replace it (e.g., for retries)</li>
     *   <li>Block as needed by calling {@code client.send()} to retry the request</li>
     * </ul>
     *
     * <p><b>Error handling:</b> If this method throws an {@link IOException}, it is passed to {@link #onError} for
     * potential recovery. This allows retry interceptors to have their failures handled by error recovery interceptors.
     *
     * <p><b>Warning for streaming exchanges:</b> When used with {@code exchange()}, the response body is a live
     * stream. Reading the body will consume it, making it unavailable to the caller. If you read the body, you must
     * provide a replacement response. Retrying is also dangerous since the request body may have already been streamed.
     *
     * @param client the HTTP client (can be used to retry the request)
     * @param request the original request
     * @param context request-scoped context for passing data between interceptors
     * @param response the response received from the server (or previous interceptor)
     * @return the response to use (same object for no change, different object to replace, or null for no change)
     * @throws IOException if an I/O error occurs (that {@link #onError} did not recover from)
     */
    default HttpResponse interceptResponse(
            HttpClient client,
            HttpRequest request,
            Context context,
            HttpResponse response
    ) throws IOException {
        return response;
    }

    /**
     * Called when an exception occurs during request execution or response interception.
     *
     * <p>This method is invoked when:
     * <ul>
     *   <li>The network request fails</li>
     *   <li>{@link #interceptResponse} throws an {@link IOException}</li>
     * </ul>
     *
     * <p>Use this hook to implement fallback responses, retry logic with backoff, or circuit breaker patterns.
     *
     * <p><b>Note:</b> Errors thrown from this method propagate directly to the caller. There is no further error
     * recovery after {@code onError}.
     *
     * @param client the HTTP client (can be used to retry the request)
     * @param request the request that failed
     * @param context request-scoped context for passing data between interceptors
     * @param exception the exception that occurred during execution
     * @return a recovery response, or null to propagate the exception to the caller
     * @throws IOException if an I/O error occurs (propagates directly to caller)
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
