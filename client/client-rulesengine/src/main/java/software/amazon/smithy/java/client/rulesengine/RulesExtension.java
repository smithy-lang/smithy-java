/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

/**
 * An SPI used to extend the rules engine with custom builtins and functions.
 */
public interface RulesExtension {
    /**
     * Mutates the given map to add name-based context-providers to the rules engine.
     *
     * @param providers Provides to add context providers to.
     */
    default void putBuiltinProviders(Map<String, Function<Context, Object>> providers) {}

    /**
     * Provides direct context key mappings for builtins that are simple Context key lookups.
     *
     * <p>This is an optimization. Builtins registered here avoid the overhead of calling a provider function and
     * instead extract values directly from the context using the key's ID, resolved at compile-time.
     *
     * @param keys Map to add builtin name to context key mappings.
     */
    default void putBuiltinKeys(Map<String, Context.Key<?>> keys) {}

    /**
     * Gets the custom functions to register with the rules engine.
     *
     * @return the functions to register.
     */
    default Iterable<RulesFunction> getFunctions() {
        return List.of();
    }

    /**
     * Allows processing a resolved endpoint, extracting properties, and updating the endpoint builder.
     *
     * @param builder The endpoint being created. Modify this based on properties and headers.
     * @param context The context provided when resolving the endpoint. The endpoint has its own context properties.
     * @param properties The raw properties returned from the endpoint resolver. Process these to update the builder.
     * @param headers The headers returned from the endpoint resolver. Process these if needed.
     */
    default void extractEndpointProperties(
            Endpoint.Builder builder,
            Context context,
            Map<String, Object> properties,
            Map<String, List<String>> headers
    ) {
        // by default does nothing.
    }
}
