/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.context.Context;

/**
 * An SPI used to extend the rules engine with custom builtins and functions.
 */
public interface RulesExtension {
    /**
     * Provides custom builtin values that are used to initialize parameters.
     *
     * @return the builtin provider or null if there is no custom provider in this extension.
     */
    default BiFunction<String, Context, Object> getBuiltinProvider() {
        return null;
    }

    /**
     * Gets a list of the custom functions to register with the VM.
     *
     * @return the list of functions to register.
     */
    default List<RulesFunction> getFunctions() {
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
