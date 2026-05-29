/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.time.Duration;
import java.util.Objects;
import software.amazon.smithy.java.context.Context;

/**
 * Per-request configuration options for HTTP requests.
 *
 * @param context Per-request context.
 * @param requestTimeout Per-request timeout override, or null to use client default.
 */
public record RequestOptions(Context context, Duration requestTimeout) {

    public RequestOptions {
        Objects.requireNonNull(context, "context");
        if (requestTimeout != null && (requestTimeout.isNegative() || requestTimeout.isZero())) {
            throw new IllegalArgumentException("requestTimeout must be positive or null: " + requestTimeout);
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
     * Returns default request options with an empty context.
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

        private Builder() {}

        /**
         * Sets the request context.
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
         * <p>Creates a new context if one hasn't been set.
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
         * Builds the RequestOptions instance.
         *
         * <p>The builder's context and request timeout are consumed by this call and reset to defaults.
         *
         * @return a new RequestOptions with the configured settings
         */
        public RequestOptions build() {
            // Take-and-replace to avoid defensive copies
            Context ctx = context != null ? context : Context.create();
            context = null;

            Duration reqTimeout = requestTimeout;
            requestTimeout = null;

            return new RequestOptions(ctx, reqTimeout);
        }
    }
}
