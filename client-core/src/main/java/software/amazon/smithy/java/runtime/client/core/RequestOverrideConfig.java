/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.List;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;

/**
 * An immutable representation of configuration overrides when invoking {@link Client#call}.
 *
 * <p>It has well-defined configuration elements that every {@link Client} needs. For extensible parts of a
 * {@link Client} that may need additional configuration, type safe configuration can be included using
 * {@link Context.Key}. It can also include {@link ClientPlugin}s that need to be applied.
 */
public final class RequestOverrideConfig {

    private final ClientTransport<?, ?> transport;
    private final ClientProtocol<?, ?> protocol;
    private final EndpointResolver endpointResolver;
    private final List<ClientInterceptor> interceptors;
    private final List<AuthScheme<?, ?>> supportedAuthSchemes;
    private final AuthSchemeResolver authSchemeResolver;
    private final List<IdentityResolver<?>> identityResolvers;
    private final Context context;
    private final List<ClientPlugin> plugins;

    protected RequestOverrideConfig(OverrideBuilder<?> builder) {
        var configBuilder = builder.configBuilder();
        this.transport = configBuilder.transport();
        this.protocol = configBuilder.protocol();
        this.endpointResolver = configBuilder.endpointResolver();
        this.interceptors = List.copyOf(configBuilder.interceptors());
        this.supportedAuthSchemes = List.copyOf(configBuilder.supportedAuthSchemes());
        this.authSchemeResolver = configBuilder.authSchemeResolver();
        this.identityResolvers = List.copyOf(configBuilder.identityResolvers());
        this.context = Context.unmodifiableCopy(configBuilder.context());
        this.plugins = List.copyOf(builder.plugins());
    }

    // Note: Making all the accessors package-private for now as they are only needed by Client, but could be public.
    ClientTransport<?, ?> transport() {
        return transport;
    }

    ClientProtocol<?, ?> protocol() {
        return protocol;
    }

    EndpointResolver endpointResolver() {
        return endpointResolver;
    }

    List<ClientInterceptor> interceptors() {
        return interceptors;
    }

    List<AuthScheme<?, ?>> supportedAuthSchemes() {
        return supportedAuthSchemes;
    }

    AuthSchemeResolver authSchemeResolver() {
        return authSchemeResolver;
    }

    List<IdentityResolver<?>> identityResolvers() {
        return identityResolvers;
    }

    Context context() {
        return context;
    }

    List<ClientPlugin> plugins() {
        return plugins;
    }

    /**
     * Create a new builder to build {@link RequestOverrideConfig}.
     *
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new builder using the same values as this object.
     *
     * @return the builder.
     */
    public Builder toBuilder() {
        Builder builder = builder()
            .transport(transport)
            .protocol(protocol)
            .endpointResolver(endpointResolver)
            .authSchemeResolver(authSchemeResolver)
            .identityResolvers(identityResolvers);
        interceptors.forEach(builder::addInterceptor);
        supportedAuthSchemes.forEach(builder::putSupportedAuthSchemes);
        return builder;
    }

    /**
     * Static builder for {@link RequestOverrideConfig}.
     */
    public static final class Builder extends OverrideBuilder<Builder> {}

    /**
     * Abstract builder for {@link RequestOverrideConfig}.
     */
    public static abstract class OverrideBuilder<B extends OverrideBuilder<B>>
        extends Client.Builder<RequestOverrideConfig, B> {
        /**
         * Creates the request override configuration.
         *
         * @return the created request override configuration.
         */
        public RequestOverrideConfig build() {
            return new RequestOverrideConfig(this);
        }
    }
}
