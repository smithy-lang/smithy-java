/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

public final class RequestContext {

    private String requestId;

    public RequestContext(Builder builder) {
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
