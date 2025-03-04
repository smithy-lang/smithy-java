/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.arguments;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.cli.commands.Command;

/**
 * A command line argument receiver.
 *
 * <p>All non-positional arguments of a {@link Command} need a
 * corresponding receiver to accept it through either
 * {@link #testOption(String, Arguments.Env)} or {@link #testParameter(String, Arguments.Env)}.
 * If any receiver rejects a non-positional argument, the CLI will
 * exit with an error.
 */
public interface ArgumentReceiver {
    /**
     * Test if the given value-less option is accepted by the receiver.
     *
     * <p>If the option is accepted, the receiver should store a stateful
     * value to indicate the option was received, and the CLI will skip
     * the argument for further processing.
     *
     * @param name Name of the option to test.
     * @return Returns true if accepted.
     */
    default boolean testOption(String name, Arguments.Env env) {
        return false;
    }

    /**
     * Test if the given parameter that requires a value is accepted by the receiver.
     *
     * <p>If the parameter is accepted, the receiver returns a Consumer that
     * receives the expected value, and it should store a stateful value to allow
     * the value to be later recalled. The CLI will skip the argument for further
     * processing.
     *
     * @param name Name of the parameter to test.
     * @return Returns a consumer if accepted or null if rejected.
     */
    default Consumer<List<String>> testParameter(String name, Arguments.Env env) {
        return null;
    }

    /**
     * A map of the flags used by the Option.
     *
     * <p>This is used to create a clear help message.
     */
    Map<Flag, String> flags();

    /**
     * Defines an argument flag.
     *
     * @param longFlag Long version of the flag, i.e. --long (REQUIRED).
     * @param shortFlag Short version of the flag, i.e. -l (optional).
     */
    record Flag(String longFlag, String shortFlag) {
        public Flag(String longFlag) {
            this(longFlag, null);
        }
    }
}
