/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

/**
 * Defines a register used in {@link RulesProgram}.
 *
 * <p>Both parameters provided as user-input to the rules engine and synthesized registers necessary for processing
 * rules have register definitions.
 *
 * @param name Name of the register.
 * @param required True if the register is a required input parameter.
 * @param defaultValue An object value that contains a default value for input parameters.
 * @param builtin A string that defines the builtin that provides a default value for input parameters.
 */
public record RegisterDefinition(String name, boolean required, Object defaultValue, String builtin) {
    public RegisterDefinition(String name) {
        this(name, false, null, null);
    }
}
