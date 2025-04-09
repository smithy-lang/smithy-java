/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import static software.amazon.smithy.modelbundle.api.StaticAuthSchemeResolver.staticScheme;

import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A ConfigProvider is used to parse a bundle of service information (model, auth configuration, endpoints, etc.) and
 * configure outgoing client calls as necessary.
 *
 * <p>Implementations of this interface can define a wrapper type that adds additional parameters to vended MCP tools.
 * For example, an AWS auth provider can make a wrapper that adds the region and AWS credential profile name as
 * arguments to tools generated for AWS APIs. A wrapper type does not need to be defined if no per-request parameters
 * need to be injected.
 *
 * <p>The ConfigProvider is responsible for configuring outbound client calls with endpoint, identity, and auth resolver
 * mechanisms. The default implementation of {@link #adaptConfig(T)} orchestrates the calls to all other ConfigProvider
 * APIs and should not be overridden. If an override is needed, the {@code super} method should be called and the
 * returned RequestOverrideConfig.Builder should be modified.
 *
 * @param <T> the type of configuration parsed by this ConfigProvider
 */
public interface ConfigProvider<T> {
    /**
     * Returns the ShapeId of the wrapper type that this config provider uses.
     *
     * @return this config provider's wrapper type, or {@code null} if it doesn't use a wrapper
     */
    default ShapeId wrapperType() {
        return null;
    }

    /**
     * Parses the given document into this ConfigProvider's {@linkplain #wrapperType() wrapper type}.
     * If this ConfigProvider has no wrapper type, this method returns null.
     *
     * @param input the document to parse
     * @return the parsed wrapper type
     */
    T parse(Document input);

    /**
     * Returns an identity resolver for the service being called, with optional values provided by the
     * parsed wrapper (if present).
     *
     * @param args the {@linkplain #parse(Document) parsed data wrapper} containing provider-specific arguments
     * @return an {@link IdentityResolver} that provides identity information
     */
    IdentityResolver<?> identityResolver(T args);

    /**
     * Returns an auth scheme for the service being called, with optional values provided by the
     * parsed wrapper (if present).
     *
     * @param args the {@linkplain #parse(Document) parsed data wrapper} containing provider-specific arguments
     * @return an {@link AuthScheme} that implements the service's required auth mechanism
     */
    AuthScheme<?, ?> authScheme(T args);

    /**
     * Returns an endpoint resolver for the service being called, with optional values provided by the
     * parsed wrapper (if present).
     *
     * @param args the {@linkplain #parse(Document) parsed data wrapper} containing provider-specific arguments
     * @return an {@link EndpointResolver} that provides the endpoint to call
     */
    EndpointResolver endpointResolver(T args);

    /**
     * Adapts an outgoing request to use the {@linkplain #authScheme(Object) auth}, {@linkplain #identityResolver(Object) identity},
     * and {@linkplain #endpointResolver(Object) endpoint} specified by this ConfigProvider.
     *
     * @param args the {@linkplain #parse(Document) parsed data wrapper} containing provider-specific arguments
     * @return a fully-configured {@link RequestOverrideConfig.Builder} that can be used to make the request
     */
    default RequestOverrideConfig.Builder adaptConfig(T args) {
        return RequestOverrideConfig.builder()
                .authSchemeResolver(StaticAuthSchemeResolver.INSTANCE)
                .putSupportedAuthSchemes(staticScheme(authScheme(args)))
                .addIdentityResolver(identityResolver(args))
                .endpointResolver(endpointResolver(args));
    }
}
