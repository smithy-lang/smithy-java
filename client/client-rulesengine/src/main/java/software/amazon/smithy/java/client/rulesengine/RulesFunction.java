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
     * Get the number of arguments the function requires.
     *
     * <p>The function will be called with this many values.
     *
     * @return the number of arguments.
     */
    int getArgumentCount();

    /**
     * Get the name of the function.
     *
     * @return the function name.
     */
    String getFunctionName();

    /**
     * Apply the function to the given N arguments and returns the result or null.
     *
     * <p>This is called when an operation has more than two arguments.
     *
     * @param arguments Arguments to process.
     * @return the result of the function or null.
     */
    default Object apply(Object... arguments) {
        throw new IllegalArgumentException("Invalid number of arguments: " + arguments.length);
    }

    /**
     * Calls a function that has zero arguments.
     *
     * @return the result of the function or null.
     */
    default Object apply0() {
        throw new IllegalArgumentException("Invalid number of arguments: 0");
    }

    /**
     * Calls a function that has one argument.
     *
     * @param arg1 Argument to process.
     * @return the result of the function or null.
     */
    default Object apply1(Object arg1) {
        throw new IllegalArgumentException("Invalid number of arguments: 1");
    }

    /**
     * Calls a function that has two arguments.
     *
     * @param arg1 Argument to process.
     * @param arg2 Argument to process.
     * @return the result of the function or null.
     */
    default Object apply2(Object arg1, Object arg2) {
        throw new IllegalArgumentException("Invalid number of arguments: 2");
    }
}
