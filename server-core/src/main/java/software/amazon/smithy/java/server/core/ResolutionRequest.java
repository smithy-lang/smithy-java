/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.net.URI;
import java.net.http.HttpHeaders;

public final class ResolutionRequest {
    private final HttpHeaders headers;
    private final URI uri;

    private ResolutionRequest(Builder builder) {
        this.headers = builder.headers;
        this.uri = builder.uri;
    }

    public static Builder builder() {
        return new Builder();
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public URI getUri() {
        return uri;
    }

    public static final class Builder {
        private HttpHeaders headers;
        private URI uri;

        private Builder() {
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
            return this;
        }

        public ResolutionRequest build() {
            return new ResolutionRequest(this);
        }
    }
}
