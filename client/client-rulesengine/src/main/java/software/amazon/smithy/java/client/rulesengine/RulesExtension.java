/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.List;
import java.util.function.BiFunction;
import software.amazon.smithy.java.context.Context;

/**
 * An SPI used to extend the rules engine with custom builtins and functions.
 */
public interface RulesExtension {
    /**
     * Provides custom builtin values that are used to initialize parameters.
     *
     * @return the builtin provider or null if there is no custom provider in this extension.
     */
    default BiFunction<String, Context, Object> getBuiltinProvider() {
        return null;
    }

    /**
     * Gets a list of the custom functions to register with the VM.
     *
     * @return the list of functions to register.
     */
    default List<RulesFunction> getFunctions() {
        return List.of();
    }
}
