/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.protocoltests;

import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.document.ProtocolTestDocument;
import software.amazon.smithy.protocoltests.traits.HttpRequestTestCase;

record RequestTestInvocationContext(
    URI endpoint,
    HttpRequestTestCase testCase,
    MockOperation mockOperation,
    Supplier<ShapeBuilder<?>> inputBuilder,
    SerializableShape manualExpectation
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
                        testCase.getMethod(),
                        testCase.getUri(),
                        testCase.getQueryParams(),
                        testCase.getHeaders(),
                        testCase.getBody(),
                        testCase.getBodyMediaType()
                    );
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
            },
            new ParameterResolver() {
                @Override
                public boolean supportsParameter(
                    ParameterContext paramCtx,
                    ExtensionContext extensionCtx
                ) throws ParameterResolutionException {
                    return paramCtx.getParameter().getType().equals(SerializableShape.class);
                }

                @Override
                public Object resolveParameter(
                    ParameterContext paramCtx,
                    ExtensionContext extensionCtx
                ) throws ParameterResolutionException {
                    if (manualExpectation() != null) {
                        return manualExpectation();
                    }
                    var builder = inputBuilder.get();
                    new ProtocolTestDocument(testCase.getParams(), testCase.getBodyMediaType().orElse(null))
                        .deserializeInto(builder);
                    return builder.build();
                }
            }
        );
    }
}
