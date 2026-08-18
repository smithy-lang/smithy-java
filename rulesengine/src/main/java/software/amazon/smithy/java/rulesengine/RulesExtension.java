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
     * Processes one generated auth-scheme endpoint property without materializing its nested maps and lists.
     *
     * <p>The default preserves the map-based extension contract. Extensions that understand auth schemes can
     * override this method to consume the field-based value directly.
     *
     * @param builder endpoint being created
     * @param context resolution context
     * @param authScheme field-based auth-scheme properties
     * @param headers resolved endpoint headers
     */
    default void extractEndpointAuthScheme(
            Endpoint.Builder builder,
            Context context,
            PropertyGetter authScheme,
            Map<String, List<String>> headers
    ) {
        Map<String, Object> entry = new java.util.HashMap<>(5);
        for (String name : List.of(
                "name",
                "signingName",
                "signingRegion",
                "disableDoubleEncoding",
                "signingRegionSet")) {
            Object value = authScheme.getProperty(name);
            if (value != null) {
                entry.put(name, value);
            }
        }
        extractEndpointProperties(
                builder,
                context,
                Map.of("authSchemes", List.of(entry)),
                headers);
    }

    /**
     * Creates an immutable auth scheme directly for a generated endpoint result.
     *
     * <p>Returning null uses the builder-based extension path.
     *
     * @param context resolution context
     * @param authScheme field-based auth-scheme properties
     * @param headers resolved endpoint headers
     * @return an immutable auth scheme, or null
     */
    default EndpointAuthScheme createEndpointAuthScheme(
            Context context,
            PropertyGetter authScheme,
            Map<String, List<String>> headers
    ) {
        return null;
    }
}
