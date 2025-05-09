/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

/**
 * Defines a parameter used in {@link RulesProgram}.
 *
 * @param name Name of the parameter.
 * @param required True if the parameter is required.
 * @param defaultValue An object value that contains a default value for input parameters.
 * @param builtin A string that defines the builtin that provides a default value for input parameters.
 */
public record ParamDefinition(String name, boolean required, Object defaultValue, String builtin) {
    public ParamDefinition(String name) {
        this(name, false, null, null);
    }
}
