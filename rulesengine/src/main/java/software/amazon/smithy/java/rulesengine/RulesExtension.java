/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;

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

    /**
     * Creates an auth scheme directly from a compiled endpoint result.
     *
     * <p>This is an optional optimization for extensions that consume the conventional
     * {@code authSchemes: [struct]} endpoint property. The supplied {@code authScheme} view is only
     * valid for the duration of this call; implementations must copy any values they retain. Other
     * endpoint properties continue to be passed to {@link #extractEndpointProperties}.
     *
     * <p>The default returns {@code null}, causing the rules engine to use
     * {@link #extractEndpointProperties} with the fully materialized property graph.
     *
     * @param authScheme flat auth-scheme fields.
     * @return the auth scheme to add to the endpoint, or null if none.
     */
    default EndpointAuthScheme createEndpointAuthScheme(PropertyGetter authScheme) {
        return null;
    }
}
