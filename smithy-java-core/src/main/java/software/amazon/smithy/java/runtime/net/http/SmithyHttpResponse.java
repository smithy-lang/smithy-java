/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.net.http;

import java.io.InputStream;
import java.net.http.HttpHeaders;
import software.amazon.smithy.java.runtime.net.StoppableInputStream;
import software.amazon.smithy.utils.SmithyBuilder;

public interface SmithyHttpResponse extends SmithyHttpMessage {

    int statusCode();

    SmithyHttpResponse withStatusCode(int statusCode);

    static Builder builder() {
        return new Builder();
    }

    final class Builder implements SmithyBuilder<SmithyHttpResponse> {

        int statusCode;
        StoppableInputStream body;
        HttpHeaders headers;
        SmithyHttpVersion httpVersion = SmithyHttpVersion.HTTP_1_1;

        private Builder() {}

        public Builder httpVersion(SmithyHttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder body(StoppableInputStream body) {
            this.body = body;
            return this;
        }

        public Builder body(InputStream body) {
            return body(StoppableInputStream.of(body));
        }

        public Builder headers(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder with(SmithyHttpResponse response) {
            this.httpVersion = response.httpVersion();
            this.headers = response.headers();
            this.body = response.body();
            this.statusCode = response.statusCode();
            return this;
        }

        @Override
        public SmithyHttpResponse build() {
            return new SmithyHttpResponseImpl(this);
        }
    }
}
