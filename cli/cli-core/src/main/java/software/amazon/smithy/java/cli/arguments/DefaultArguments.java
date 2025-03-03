/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.arguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.cli.CliError;

// TODO: Could this be merged with parent interface? Will there really ever be more implementations?
// TODO: the peek/shift logic here can probably be simplified.
final class DefaultArguments implements Arguments {

    private final Map<Class<? extends ArgumentReceiver>, ArgumentReceiver> receivers = new LinkedHashMap<>();
    private final String[] args;
    private final Arguments.Env env;
    private int position = 0;

    DefaultArguments(String[] args, Env env) {
        this.args = args;
        this.env = env;
    }

    @Override
    public void addReceiver(ArgumentReceiver receiver) {
        receivers.put(receiver.getClass(), receiver);
    }

    @Override
    public boolean hasReceiver(Class<? extends ArgumentReceiver> receiverClass) {
        return receivers.containsKey(receiverClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ArgumentReceiver> T expectReceiver(Class<T> type) {
        return (T) Objects.requireNonNull(receivers.get(type));
    }

    @Override
    public Iterable<ArgumentReceiver> getReceivers() {
        return receivers.values();
    }

    @Override
    public boolean hasNext() {
        return position < args.length;
    }

    @Override
    public String peek() {
        String peek = hasNext() ? args[position] : null;

        if (peek != null) {
            for (ArgumentReceiver interceptor : receivers.values()) {
                if (interceptor.testOption(peek, env)) {
                    position++;
                    return peek();
                }

                Consumer<List<String>> optionConsumer = interceptor.testParameter(peek, env);
                if (optionConsumer != null) {
                    position++;
                    optionConsumer.accept(accumulateFor(peek));
                    return peek();
                }
            }
        }

        return peek;
    }

    @Override
    public String shift() {
        String peek = peek();

        if (peek != null) {
            position++;
        }

        return peek;
    }

    @Override
    public List<String> accumulateFor(String parameter) {
        if (!hasNext()) {
            throw new CliError("Expected arguments for '" + parameter + "'");
        }

        // Accumulate positional arguments until we hit the end of the inputs or another parameter.
        List<String> results = new ArrayList<>();
        while (hasNext()) {
            String next = args[position];
            // Next value is another parameter, stop accumulating.
            if (next.startsWith("-")) {
                return results;
            }
            results.add(next);
            position++;
        }
        return results;
    }

    @Override
    public void complete() {
        while (hasNext()) {
            String next = peek();
            // We expect that ALL arguments are consumed by a receiver. Anything not consumed
            // is an illegal input.
            if (next != null) {
                throw new CliError(
                        "Unexpected CLI input: "
                                + Arrays.toString(Arrays.copyOfRange(args, position, args.length)));
            }
        }
    }
}
