/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

/**
 * Represents a Lambda proxy integration request context.
 *
 * @see <a href="https://docs.aws.amazon.com/apigateway/latest/developerguide/set-up-lambda-proxy-integrations.html#api-gateway-simple-proxy-for-lambda-input-format">API Gateway Lambda Proxy Integration > Request Context</a>
 * <br>
 * Note: Not all fields are currently supported.
 */
final class RequestContext {

    private String requestId;

    private RequestContext(Builder builder) {
        this.requestId = builder.requestId;
    }

    public String getRequestId() {
        return requestId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }

    // This and the setters only exist so that Lambda can use this POJO when serializing the event
    private RequestContext() {
    }

    private void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
