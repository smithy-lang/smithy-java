/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import software.amazon.smithy.java.context.Context;

/**
 * Defines a parameter used in {@link Bytecode}.
 *
 * @param name Name of the parameter.
 * @param required True if the parameter is required.
 * @param defaultValue An object value that contains a default value for input parameters.
 * @param builtin A string that defines the builtin that provides a default value for input parameters.
 * @param builtinKey Direct context key for simple builtins (avoids provider function call).
 * @param temp True if this is a temporary register that does not take initial values.
 */
public record RegisterDefinition(
        String name,
        boolean required,
        Object defaultValue,
        String builtin,
        Context.Key<?> builtinKey,
        boolean temp) {
    public RegisterDefinition(String name, boolean required, Object defaultValue, String builtin, boolean temp) {
        this(name, required, defaultValue, builtin, null, temp);
    }
}
