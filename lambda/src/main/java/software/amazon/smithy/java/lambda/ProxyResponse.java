/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.lambda;

import java.util.List;
import java.util.Map;

public class ProxyResponse {
    private final Integer statusCode;
    private final Map<String, String> headers;
    private final Map<String, List<String>> multiValueHeaders;
    private final String body;
    private final Boolean isBase64Encoded;

    private ProxyResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.headers = builder.headers;
        this.multiValueHeaders = builder.multiValueHeaders;
        this.body = builder.body;
        this.isBase64Encoded = builder.isBase64Encoded;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, List<String>> getMultiValueHeaders() {
        return multiValueHeaders;
    }

    public String getBody() {
        return body;
    }

    public Boolean getIsBase64Encoded() {
        return isBase64Encoded;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer statusCode;
        private Map<String, String> headers;
        private Map<String, List<String>> multiValueHeaders;
        private String body;
        private Boolean isBase64Encoded;

        public Builder statusCode(Integer statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder multiValueHeaders(Map<String, List<String>> multiValueHeaders) {
            this.multiValueHeaders = multiValueHeaders;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder isBase64Encoded(Boolean isBase64Encoded) {
            this.isBase64Encoded = isBase64Encoded;
            return this;
        }

        public ProxyResponse build() {
            return new ProxyResponse(this);
        }
    }

}
