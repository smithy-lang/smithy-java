/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

/**
 * Implements a function that can be used in the rules engine.
 */
public interface RulesFunction {
    /**
     * Get the number of operands the function requires.
     *
     * <p>The function will be called with this many values.
     *
     * @return the number of operands.
     */
    int getOperandCount();

    /**
     * Get the name of the function.
     *
     * @return the function name.
     */
    String getFunctionName();

    /**
     * Apply the function to the given N operands and returns the result or null.
     *
     * <p>This is called when an operation has more than two operands.
     *
     * @param operands Operands to process.
     * @return the result of the function or null.
     */
    default Object apply(Object... operands) {
        throw new IllegalArgumentException("Invalid number of arguments: " + operands.length);
    }

    /**
     * Calls a function that has zero operands.
     *
     * @return the result of the function or null.
     */
    default Object apply0() {
        throw new IllegalArgumentException("Invalid number of arguments: 0");
    }

    /**
     * Calls a function that has one operand.
     *
     * @param arg1 Operand to process.
     * @return the result of the function or null.
     */
    default Object apply1(Object arg1) {
        throw new IllegalArgumentException("Invalid number of arguments: 1");
    }

    /**
     * Calls a function that has two operands.
     *
     * @param arg1 Operand to process.
     * @param arg2 Operand to process.
     * @return the result of the function or null.
     */
    default Object apply2(Object arg1, Object arg2) {
        throw new IllegalArgumentException("Invalid number of arguments: 2");
    }
}
