/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

/**
 * A test invocation whose setup (building the codegen or dynamic model, deserializing the test document, etc.) threw.
 *
 * <p>Building a single invocation context happens while the test template stream is generated. If that throws, JUnit
 * reports a single opaque {@code executionError} and drops every remaining invocation for the method. Wrapping the
 * failure in its own invocation keeps one bad case from hiding all the others and attributes the failure to a specific
 * test id and {@link TestMode}.
 */
record FailedGenerationTestCase(String displayName, Throwable cause) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return displayName;
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        // Resolve any parameter type (the request test takes DataStream args, the response test takes a Runnable) by
        // throwing, so the failure is attributed to this single invocation.
        return List.of(new ParameterResolver() {
            @Override
            public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
                return true;
            }

            @Override
            public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
                throw new AssertionError("Failed to set up protocol test " + displayName, cause);
            }
        });
    }
}
