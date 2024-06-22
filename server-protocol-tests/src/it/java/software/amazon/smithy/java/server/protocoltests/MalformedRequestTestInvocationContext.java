/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocoltests;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.protocoltests.traits.HttpMalformedRequestTestCase;
import software.amazon.smithy.protocoltests.traits.HttpMalformedResponseBodyDefinition;

record MalformedRequestTestInvocationContext(
    URI endpoint,
    HttpMalformedRequestTestCase testCase,
    MockOperation mockOperation
)
    implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return testCase.getId();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return parameterContext.getParameter().getType() == String.class;
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return testCase.getId();
                }
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext paramCtx,
                    ExtensionContext extensionCtx
                ) throws ParameterResolutionException {
                    return paramCtx.getParameter().getType().equals(EndToEndProtocolTests.HttpRequest.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return new EndToEndProtocolTests.HttpRequest(
                        testCase.getRequest().getMethod(),
                        testCase.getRequest().getUri().orElse("/"),
                        testCase.getRequest().getQueryParams(),
                        testCase.getRequest().getHeaders(),
                        testCase.getRequest().getBody(),
                        Optional.empty()
                    );
                }
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext paramCtx,
                    ExtensionContext extensionCtx
                ) throws ParameterResolutionException {
                    return paramCtx.getParameter().getType().equals(EndToEndProtocolTests.HttpResponse.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return new EndToEndProtocolTests.HttpResponse(
                        testCase.getResponse().getCode(),
                        testCase.getResponse().getHeaders(),
                        Optional.empty(),
                        Optional.empty()
                    );
                }
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext paramCtx,
                    ExtensionContext extensionCtx
                ) throws ParameterResolutionException {
                    return paramCtx.getParameter().getType().equals(HttpMalformedResponseBodyDefinition.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return testCase.getResponse().getBody().orElse(null);
                }
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return parameterContext.getParameter().getType().equals(MockOperation.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return mockOperation;
                }
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return parameterContext.getParameter().getType().equals(URI.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext
                ) throws ParameterResolutionException {
                    return endpoint;
                }
            }
        );
    }
}
