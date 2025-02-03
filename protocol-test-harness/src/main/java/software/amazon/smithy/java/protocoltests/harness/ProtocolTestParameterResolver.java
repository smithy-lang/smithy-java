/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * ParameterResolver for protocol test cases. Resolves {@code Callable<ProtocolTestResult>} parameters.
 */
@FunctionalInterface
interface ProtocolTestParameterResolver extends ParameterResolver {
    @Override
    default boolean supportsParameter(
            ParameterContext paramCtx,
            ExtensionContext extCtx
    ) throws ParameterResolutionException {
        return paramCtx.getParameter().getType().equals(Runnable.class);
    }

    @Override
    default Object resolveParameter(
            ParameterContext paramCtx,
            ExtensionContext extContext
    ) throws ParameterResolutionException {
        return (Runnable) this::test;
    }

    void test();
}
