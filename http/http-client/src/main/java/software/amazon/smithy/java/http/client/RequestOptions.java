/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.time.Duration;
import java.util.Objects;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Per-request configuration. Every option is nullable; a null value falls back to the corresponding
 * client-level default (or, for {@link #expectContinue()}, to the request's {@code Expect} header).
 */
public final class RequestOptions {

    private static final String CONTINUE = "100-continue";
    private static final RequestOptions DEFAULTS = builder().build();

    private final Duration requestTimeout;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration acquireTimeout;
    private final Boolean expectContinue;

    private RequestOptions(Builder b) {
        this.requestTimeout = b.requestTimeout;
        this.connectTimeout = b.connectTimeout;
        this.readTimeout = b.readTimeout;
        this.acquireTimeout = b.acquireTimeout;
        this.expectContinue = b.expectContinue;
    }

    /**
     * @return default (all-null) request options.
     */
    public static RequestOptions defaults() {
        return DEFAULTS;
    }

    /**
     * @return a new builder for {@link RequestOptions}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Overall per-request timeout (the whole send), or null for the client default.
     */
    public Duration requestTimeout() {
        return requestTimeout;
    }

    /**
     * TCP connect timeout for a new connection, or null for the client default.
     */
    public Duration connectTimeout() {
        return connectTimeout;
    }

    /**
     * Socket read / inactivity timeout, or null for the client default.
     */
    public Duration readTimeout() {
        return readTimeout;
    }

    /**
     * Max wait to obtain a connection from the pool, or null for the client default.
     */
    public Duration acquireTimeout() {
        return acquireTimeout;
    }

    /**
     * {@code Expect: 100-continue} handling: {@code TRUE} adds the header if absent, {@code FALSE}
     * suppresses it even if the request carries it, {@code null} defers to the request's {@code Expect}
     * header (the default behavior).
     *
     * <p>The full handshake (sending only the headers, then waiting for an interim {@code 100} response
     * before writing the body) is performed on HTTP/1.1 only. On HTTP/2 this toggle controls only whether
     * the header is on the wire; the request body is sent without waiting.
     */
    public Boolean expectContinue() {
        return expectContinue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RequestOptions that = (RequestOptions) o;
        return Objects.equals(requestTimeout, that.requestTimeout)
                && Objects.equals(connectTimeout, that.connectTimeout)
                && Objects.equals(readTimeout, that.readTimeout)
                && Objects.equals(acquireTimeout, that.acquireTimeout)
                && Objects.equals(expectContinue, that.expectContinue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestTimeout, connectTimeout, readTimeout, acquireTimeout, expectContinue);
    }

    @Override
    public String toString() {
        return "RequestOptions{acquireTimeout=" + acquireTimeout + ", requestTimeout=" + requestTimeout +
                ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", expectContinue=" + expectContinue + '}';
    }

    /**
     * Returns {@code request} with its {@code Expect: 100-continue} header normalized to match
     * {@link #expectContinue()}, so the on-the-wire headers and the client's continue-handshake decision
     * stay consistent. {@code TRUE} adds the header if absent, {@code FALSE} strips it, {@code null}
     * leaves the request untouched.
     *
     * <p>When a change is required it is applied via {@link HttpRequest#toModifiable()}: an already-modifiable
     * request is mutated in place (and returned as the same instance), while an immutable one is copied and the copy
     * mutated.
     *
     * @param request the request to normalize
     * @return the request with its {@code Expect} header normalized
     */
    public HttpRequest applyExpectContinue(HttpRequest request) {
        if (expectContinue == null) {
            return request;
        }

        String header = request.headers().firstValue(HeaderName.EXPECT);
        boolean present = header != null && header.equalsIgnoreCase(CONTINUE);
        if (expectContinue) {
            return present
                    ? request
                    : request.toModifiable().setHeader(HeaderName.EXPECT, CONTINUE);
        } else if (!present) {
            return request;
        } else {
            return request.toModifiable().removeHeader(HeaderName.EXPECT);
        }
    }

    /**
     * Builder for {@link RequestOptions}. Every setter is optional; an unset (or null) value falls back to
     * the client-level default for that option.
     */
    public static final class Builder {
        private Duration requestTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration acquireTimeout;
        private Boolean expectContinue;

        private Builder() {}

        /**
         * Sets the overall timeout for the entire send, or null to use the client default.
         *
         * @param requestTimeout the timeout; must be positive if non-null
         * @return this builder
         * @throws IllegalArgumentException if {@code requestTimeout} is zero or negative
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositiveOrNull(requestTimeout, "requestTimeout");
            return this;
        }

        /**
         * Sets the TCP connect timeout for establishing a new connection, or null to use the client default.
         *
         * @param connectTimeout the timeout; must be positive if non-null
         * @return this builder
         * @throws IllegalArgumentException if {@code connectTimeout} is zero or negative
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = requirePositiveOrNull(connectTimeout, "connectTimeout");
            return this;
        }

        /**
         * Sets the socket read / inactivity timeout, or null to use the client default.
         *
         * @param readTimeout the timeout; must be positive if non-null
         * @return this builder
         * @throws IllegalArgumentException if {@code readTimeout} is zero or negative
         */
        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = requirePositiveOrNull(readTimeout, "readTimeout");
            return this;
        }

        /**
         * Sets the maximum time to wait for a connection from the pool, or null to use the client default.
         *
         * @param acquireTimeout the timeout; must be positive if non-null
         * @return this builder
         * @throws IllegalArgumentException if {@code acquireTimeout} is zero or negative
         */
        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = requirePositiveOrNull(acquireTimeout, "acquireTimeout");
            return this;
        }

        /**
         * Controls {@code Expect: 100-continue} handling for this request: {@code TRUE} adds the header if
         * absent, {@code FALSE} suppresses it even if the request carries the header, and {@code null} (the
         * default) defers to the request's {@code Expect} header. The full wait-for-{@code 100} handshake is
         * performed on HTTP/1.1 only; on HTTP/2 this toggles only the header. See {@link #expectContinue()}.
         *
         * @param expectContinue the toggle, or null to defer to the request header
         * @return this builder
         */
        public Builder expectContinue(Boolean expectContinue) {
            this.expectContinue = expectContinue;
            return this;
        }

        /**
         * @return a new {@link RequestOptions} with the values configured on this builder.
         */
        public RequestOptions build() {
            return new RequestOptions(this);
        }

        private static Duration requirePositiveOrNull(Duration d, String name) {
            if (d != null && (d.isNegative() || d.isZero())) {
                throw new IllegalArgumentException(name + " must be positive or null: " + d);
            }
            return d;
        }
    }
}
