/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.context.Context;

/**
 * Per-request configuration options for HTTP requests.
 *
 * <p>Request interceptors are applied after client-level interceptors, allowing
 * per-request customization while preserving client defaults.
 *
 * <p>Example usage:
 * <pre>{@code
 * RequestOptions options = RequestOptions.builder()
 *     .putContext(TRACE_ID_KEY, traceId)
 *     .addInterceptor(new LoggingInterceptor())
 *     .build();
 *
 * HttpResponse response = client.send(request, options);
 * }</pre>
 *
 * @see HttpClient#send(software.amazon.smithy.java.http.api.HttpRequest, RequestOptions)
 * @see HttpClient#exchange(software.amazon.smithy.java.http.api.HttpRequest, RequestOptions)
 */
public final class RequestOptions {
    private final Context context;
    private final List<HttpInterceptor> interceptors;

    private RequestOptions(Builder builder) {
        this.context = builder.context == null ? Context.create() : builder.context;
        this.interceptors = builder.interceptors == null ? List.of() : builder.interceptors;
        builder.interceptors = null;
        builder.context = null;
    }

    /**
     * Returns the request context for passing data to interceptors.
     *
     * @return the request context, never null
     */
    public Context context() {
        return context;
    }

    /**
     * Returns the request-specific interceptors.
     *
     * @return immutable list of interceptors configured for this request
     */
    public List<HttpInterceptor> interceptors() {
        return interceptors;
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
        private Context context = null;
        private List<HttpInterceptor> interceptors;

        private Builder() {}

        /**
         * Sets the request context.
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
            return new RequestOptions(this);
        }
    }
}
