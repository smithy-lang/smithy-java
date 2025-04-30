/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.endpointrules;

/**
 * Implements a function that can be used in the rules engine.
 */
public interface VmFunction {
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
     * Apply the function to the given operands and returns the result or null.
     *
     * @param operands Operands to process.
     * @return the result of the function or null.
     */
    Object apply(Object... operands);
}
