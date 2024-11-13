/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.integrations.lambda;

import java.util.List;
import java.util.Map;

public final class ProxyRequest {
    private Map<String, String> pathParameters;
    private Map<String, String> stageVariables;
    private String resource;
    private String path;
    private String httpMethod;
    private Map<String, List<String>> multiValueHeaders;
    private Map<String, List<String>> multiValueQueryStringParameters;
    private RequestContext requestContext;
    private String body;

    private ProxyRequest(Builder builder) {
        this.pathParameters = builder.pathParameters;
        this.stageVariables = builder.stageVariables;
        this.resource = builder.resource;
        this.path = builder.path;
        this.httpMethod = builder.httpMethod;
        this.multiValueHeaders = builder.multiValueHeaders;
        this.multiValueQueryStringParameters = builder.multiValueQueryStringParameters;
        this.requestContext = builder.requestContext;
        this.body = builder.body;
    }

    private void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    private void setBody(String body) {
        this.body = body;
    }

    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    public Map<String, String> getStageVariables() {
        return stageVariables;
    }

    public String getResource() {
        return resource;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Map<String, List<String>> getMultiValueHeaders() {
        return multiValueHeaders;
    }

    public Map<String, List<String>> getMultiValueQueryStringParameters() {
        return multiValueQueryStringParameters;
    }

    public RequestContext getRequestContext() {
        return requestContext;
    }

    public String getBody() {
        return body;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, String> pathParameters;
        private Map<String, String> stageVariables;
        private String resource;
        private String path;
        private String httpMethod;
        private Map<String, List<String>> multiValueHeaders;
        private Map<String, List<String>> multiValueQueryStringParameters;
        private RequestContext requestContext;
        private String body;

        public Builder pathParameters(Map<String, String> pathParameters) {
            this.pathParameters = pathParameters;
            return this;
        }

        public Builder stageVariables(Map<String, String> stageVariables) {
            this.stageVariables = stageVariables;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder multiValueHeaders(Map<String, List<String>> multiValueHeaders) {
            this.multiValueHeaders = multiValueHeaders;
            return this;
        }

        public Builder multiValueQueryStringParameters(Map<String, List<String>> multiValueQueryStringParameters) {
            this.multiValueQueryStringParameters = multiValueQueryStringParameters;
            return this;
        }

        public Builder requestContext(RequestContext requestContext) {
            this.requestContext = requestContext;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public ProxyRequest build() {
            return new ProxyRequest(this);
        }
    }

    // This and the setters only exist so that Lambda can use this POJO when serializing the event
    private ProxyRequest() {}

    private void setPathParameters(Map<String, String> pathParameters) {
        this.pathParameters = pathParameters;
    }

    private void setStageVariables(Map<String, String> stageVariables) {
        this.stageVariables = stageVariables;
    }

    private void setResource(String resource) {
        this.resource = resource;
    }

    private void setPath(String path) {
        this.path = path;
    }

    private void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    private void setMultiValueHeaders(Map<String, List<String>> multiValueHeaders) {
        this.multiValueHeaders = multiValueHeaders;
    }

    private void setMultiValueQueryStringParameters(Map<String, List<String>> multiValueQueryStringParameters) {
        this.multiValueQueryStringParameters = multiValueQueryStringParameters;
    }
}
