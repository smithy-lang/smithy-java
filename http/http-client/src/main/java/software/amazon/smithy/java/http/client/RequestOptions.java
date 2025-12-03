/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;

/**
 * Per-request configuration options for HTTP requests.
 *
 * <p>Example usage:
 * {@snippet :
 * RequestOptions options = RequestOptions.builder()
 *     .putContext(TRACE_ID_KEY, traceId)
 *     .addInterceptor(new LoggingInterceptor())
 *     .build();
 *
 * HttpResponse response = client.send(request, options);
 * }
 *
 * @see HttpClient#send(software.amazon.smithy.java.http.api.HttpRequest, RequestOptions)
 * @see HttpClient#newExchange(software.amazon.smithy.java.http.api.HttpRequest, RequestOptions)
 *
 * @param context Request context used with interceptors
 * @param requestTimeout Per-request timeout override, or null to use client default.
 * @param interceptors Interceptors to add to the request in addition to client-wide interceptors.
 */
public record RequestOptions(Context context, Duration requestTimeout, List<HttpInterceptor> interceptors) {

    public RequestOptions {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(interceptors, "interceptors");
        if (requestTimeout != null && (requestTimeout.isNegative() || requestTimeout.isZero())) {
            throw new IllegalArgumentException("requestTimeout must be positive or null: " + requestTimeout);
        }
    }

    /**
     * Resolves the final list of interceptors by combining client and request interceptors.
     *
     * <p>Client interceptors are applied first, followed by request-specific interceptors.
     * This ordering allows request interceptors to override or extend client behavior.
     *
     * @param clientInterceptors interceptors configured on the HTTP client
     * @return combined list with client interceptors first, then request interceptors
     */
    public List<HttpInterceptor> resolveInterceptors(List<HttpInterceptor> clientInterceptors) {
        if (clientInterceptors.isEmpty()) {
            return interceptors;
        } else if (interceptors.isEmpty()) {
            return clientInterceptors;
        } else {
            List<HttpInterceptor> resolved = new ArrayList<>(interceptors.size() + clientInterceptors.size());
            resolved.addAll(clientInterceptors);
            resolved.addAll(interceptors);
            return resolved;
        }
    }

    /**
     * Creates a new builder for RequestOptions.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns default request options with an empty context and no interceptors.
     *
     * @return default request options
     */
    public static RequestOptions defaults() {
        return builder().build();
    }

    /**
     * Builder for creating RequestOptions instances.
     */
    public static final class Builder {
        private Context context;
        private Duration requestTimeout;
        private List<HttpInterceptor> interceptors;

        private Builder() {}

        /**
         * Sets the mutable request context.
         *
         * <p>The context can be used to pass request-scoped data to interceptors.
         *
         * @param context the context to use for this request
         * @return this builder
         */
        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        /**
         * Adds a key-value pair to the request context.
         *
         * <p>Creates a new context if one hasn't been set. This is a convenience
         * method for adding individual context values without creating a Context first.
         *
         * @param key the context key
         * @param value the value to associate with the key
         * @param <T> the type of the context value
         * @return this builder
         */
        public <T> Builder putContext(Context.Key<T> key, T value) {
            if (context == null) {
                context = Context.create();
            }
            this.context.put(key, value);
            return this;
        }

        /**
         * Sets the request timeout for this specific request.
         *
         * <p>Overrides the client-level timeout. Set to null to use the client default.
         *
         * @param timeout the timeout duration, or null for client default
         * @return this builder
         */
        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        /**
         * Adds an interceptor to the request.
         *
         * <p>Request interceptors are applied after client-level interceptors.
         * Multiple interceptors can be added and will be applied in the order added.
         *
         * @param interceptor the interceptor to add
         * @return this builder
         */
        public Builder addInterceptor(HttpInterceptor interceptor) {
            if (interceptors == null) {
                interceptors = new ArrayList<>();
            }
            this.interceptors.add(interceptor);
            return this;
        }

        /**
         * Sets the list of request interceptors, replacing any previously added.
         *
         * @param interceptors the interceptors to use for this request
         * @return this builder
         */
        public Builder interceptors(List<HttpInterceptor> interceptors) {
            if (this.interceptors == null) {
                this.interceptors = new ArrayList<>(interceptors);
            } else {
                this.interceptors.clear();
                this.interceptors.addAll(interceptors);
            }
            return this;
        }

        /**
         * Builds the RequestOptions instance.
         *
         * @return a new RequestOptions with the configured settings
         */
        public RequestOptions build() {
            // Take-and-replace to avoid defensive copies
            Context ctx = context != null ? context : Context.create();
            context = null;

            List<HttpInterceptor> ints = interceptors != null ? interceptors : List.of();
            interceptors = null;

            return new RequestOptions(ctx, requestTimeout, ints);
        }
    }
}
