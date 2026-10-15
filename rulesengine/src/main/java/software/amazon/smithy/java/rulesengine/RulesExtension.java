/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.endpoints.Endpoint;
import software.amazon.smithy.java.endpoints.EndpointAuthScheme;
import software.amazon.smithy.utils.SmithyUnstableApi;

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
     * Optionally compiles a statically known endpoint property shape into a direct endpoint binding.
     *
     * <p>This method is called while loading a ruleset, not while resolving an endpoint. A binding can
     * retain the supplied property views and resolve only the dynamic values it needs for each endpoint.
     * This avoids rebuilding the complete raw property bag before passing it to an extension.
     *
     * <p>The default implementation returns null, which selects the compatibility path that materializes
     * the property bag and invokes {@link #extractEndpointProperties}. Extensions should also return null
     * for any shape they cannot bind completely.
     *
     * @param properties load-time view of the raw endpoint property bag.
     * @return a direct endpoint binding, or null when the materialized compatibility path is required.
     */
    @SmithyUnstableApi
    default EndpointPropertyBinding bindEndpointProperties(EndpointPropertyBinding.Properties properties) {
        return null;
    }

    /** Projects a compiled raw property shape directly onto an endpoint. */
    @SmithyUnstableApi
    interface EndpointPropertyBinding {
        default Set<Context.Key<?>> properties() {
            return Set.of();
        }

        default Object property(Context.Key<?> property, ResolutionValues values) {
            return null;
        }

        default int authSchemeCount() {
            return 0;
        }

        /**
         * Resolves an auth-scheme candidate.
         *
         * @return the scheme, or null when the raw candidate is malformed and should be skipped.
         */
        default EndpointAuthScheme authScheme(int index, ResolutionValues values) {
            throw new IndexOutOfBoundsException(index);
        }

        /**
         * Opaque handle to the dynamic values retained by one projected endpoint.
         *
         * <p>Bindings must not inspect or retain this handle. They pass it to
         * {@link Properties#resolve(ResolutionValues)} when resolving a dynamic property.
         */
        interface ResolutionValues {}

        /** A load-time view of an endpoint property value. */
        interface Properties {
            Properties get(String name);

            Properties get(int index);

            boolean isObject();

            boolean isList();

            int size();

            boolean isConstant();

            Object constantValue();

            Object resolve(ResolutionValues values);
        }
    }
}
