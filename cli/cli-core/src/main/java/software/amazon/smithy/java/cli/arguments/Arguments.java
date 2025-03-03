/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.arguments;

import java.util.List;
import software.amazon.smithy.java.cli.CLIClient;
import software.amazon.smithy.java.cli.CliError;

/**
 * Command line arguments list to evaluate.
 *
 * <p>Arguments are parsed on demand. To finalize parsing arguments and force validation of remaining arguments,
 * call {@link #complete()}. Note that because arguments are parsed on demand and whole set of arguments isn't
 * known before {@link #complete()} is called.
 */
// TODO: Maybe make this an iterator?
public interface Arguments {

    static Arguments of(String[] args, Env env) {
        return new DefaultArguments(args, env);
    }

    /**
     * Adds an argument receiver to the argument list.
     *
     * @param receiver Receiver to add.
     */
    void addReceiver(ArgumentReceiver receiver);

    /**
     * Check if this class contains a receiver.
     *
     * @param receiverClass Class of receiver to detect.
     * @return Returns true if found.
     */
    boolean hasReceiver(Class<? extends ArgumentReceiver> receiverClass);

    /**
     * Get a receiver by class, throwing and error if the receiver is not found.
     *
     * @param type Type of receiver to get.
     * @param <T> Type of receiver to get.
     * @return Returns the found receiver.
     * @throws NullPointerException if not found.
     */
    <T extends ArgumentReceiver> T expectReceiver(Class<T> type);

    /**
     * Get the argument receivers registered with the Arguments list.
     *
     * @return Returns the receivers.
     */
    Iterable<ArgumentReceiver> getReceivers();

    /**
     * Checks if any arguments are remaining.
     *
     * @return Returns true if there are more arguments.
     */
    boolean hasNext();

    /**
     * Peeks at the next value in the argument list without shifting it.
     *
     * <p>Note that arguments consumed by a {@link ArgumentReceiver} are never peeked.
     *
     * @return Returns the next argument or null if no further arguments are present.
     */
    String peek();

    /**
     * Shifts off the next value in the argument list or returns null.
     *
     * @return Returns the next value or null.
     */
    String shift();

    /**
     * Accumulates argument values for a parameter by name.
     *
     * @param parameter Name of the parameter to get the values of.
     * @return Returns the values of the parameter.
     * @throws CliError if the parameter is not present.
     */
    List<String> accumulateFor(String parameter);

    /**
     * Completes parsing of arguments
     *
     * <p>If any argument is encountered that isn't valid for a registered Receiver, then an error is raised.
     *
     * <p>Subscribers for different receivers are called when this method is the first called.
     *
     * @throws CliError if the next argument starts with "--" or "-" and isn't valid for a registered receiver.
     */
    void complete();

    /**
     * Environment data available to Argument
     *
     * <p>This environment data allows argument receivers to be used to configure the clients before execution by
     * a command.
     */
    record Env(CLIClient.Builder clientBuilder) {}
}
