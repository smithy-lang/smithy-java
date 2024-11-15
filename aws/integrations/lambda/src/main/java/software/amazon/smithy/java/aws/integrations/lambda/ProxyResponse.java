/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Represents a Lambda proxy integration response.
 *
 * @see <a href="https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-output-format">API Gateway Lambda Proxy Integration</a>
 * <br>
 * Note: Not all fields are currently supported.
 */
final class ProxyResponse {
    private final Integer statusCode;
    private final Map<String, String> headers;
    private final Map<String, List<String>> multiValueHeaders;
    private final String body;
    private final Boolean isBase64Encoded;

    private ProxyResponse(Builder builder) {
        this.statusCode = builder.statusCode;
        this.headers = Collections.unmodifiableMap(builder.headers);
        this.multiValueHeaders = Collections.unmodifiableMap(builder.multiValueHeaders);
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
        private Map<String, String> headers = Collections.emptyMap();
        private Map<String, List<String>> multiValueHeaders = Collections.emptyMap();
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
