/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.client.endpoint.api.Endpoint;
import software.amazon.smithy.java.runtime.client.endpoint.api.EndpointResolver;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PersonDirectory;
import software.amazon.smithy.java.runtime.example.model.PersonDirectoryAsync;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;

// Example of a potentially generated client.
public final class PersonDirectoryClient implements PersonDirectory {

    private final PersonDirectoryAsync asyncClient;

    private PersonDirectoryClient(Builder builder) {
        PersonDirectoryAsyncClient.Builder syncClientBuilder = PersonDirectoryAsyncClient.builder()
            .transport(builder.transport)
            .endpointResolver(builder.endpointResolver)
            .authSchemeResolver(builder.authSchemeResolver)
            .identityResolvers(builder.identityResolvers);

        builder.interceptors.forEach(syncClientBuilder::addInterceptor);
        builder.supportedAuthSchemes.forEach(syncClientBuilder::putSupportedAuthSchemes);

        asyncClient = syncClientBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public PutPersonOutput putPerson(PutPersonInput input, Context context) {
        return asyncClient.putPerson(input, context).join();
    }

    @Override
    public PutPersonImageOutput putPersonImage(PutPersonImageInput input, Context context) {
        return asyncClient.putPersonImage(input, context).join();
    }

    @Override
    public GetPersonImageOutput getPersonImage(GetPersonImageInput input, Context context) {
        return asyncClient.getPersonImage(input, context).join();
    }

    public static final class Builder {

        private ClientTransport transport;
        private EndpointResolver endpointResolver;
        private final List<ClientInterceptor> interceptors = new ArrayList<>();
        private AuthSchemeResolver authSchemeResolver;
        private final List<AuthScheme<?, ?>> supportedAuthSchemes = new ArrayList<>();
        private final List<IdentityResolver<?>> identityResolvers = new ArrayList<>();

        private Builder() {}

        /**
         * Set the protocol and transport to when calling the service.
         *
         * @param transport Client transport used to send requests.
         * @return Returns the builder.
         */
        public Builder transport(ClientTransport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Set the resolver used to resolve endpoints.
         *
         * @param endpointResolver Endpoint resolver to use to resolve endpoints.
         * @return Returns the endpoint resolver.
         */
        public Builder endpointResolver(EndpointResolver endpointResolver) {
            this.endpointResolver = endpointResolver;
            return this;
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(Endpoint endpoint) {
            return endpointResolver(EndpointResolver.staticEndpoint(endpoint));
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(URI endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Configure the client to use a static endpoint.
         *
         * @param endpoint Endpoint to connect to.
         * @return the builder.
         */
        public Builder endpoint(String endpoint) {
            return endpoint(Endpoint.builder().uri(endpoint).build());
        }

        /**
         * Add an interceptor to the client.
         *
         * @param interceptor Interceptor to add.
         * @return the builder.
         */
        public Builder addInterceptor(ClientInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        /**
         * Set the auth scheme resolver of the client.
         *
         * @param authSchemeResolver Auth scheme resolver to use.
         * @return the builder.
         */
        public Builder authSchemeResolver(AuthSchemeResolver authSchemeResolver) {
            this.authSchemeResolver = authSchemeResolver;
            return this;
        }

        /**
         * Add supported auth schemes to the client that works in tandem with the {@link AuthSchemeResolver}.
         *
         * <p> If the scheme ID is already supported, it will be replaced by the provided auth scheme.
         *
         * @param authSchemes Auth schemes to add.
         * @return the builder.
         */
        public Builder putSupportedAuthSchemes(AuthScheme<?, ?>... authSchemes) {
            supportedAuthSchemes.addAll(Arrays.asList(authSchemes));
            return this;
        }

        /**
         * Add identity resolvers to the client.
         *
         * @param identityResolvers Identity resolvers to add.
         * @return the builder.
         */
        public Builder addIdentityResolver(IdentityResolver<?>... identityResolvers) {
            this.identityResolvers.addAll(Arrays.asList(identityResolvers));
            return this;
        }

        /**
         * Set the identity resolvers of the client.
         *
         * @param identityResolvers Identity resolvers to set.
         * @return the builder.
         */
        public Builder identityResolvers(List<IdentityResolver<?>> identityResolvers) {
            this.identityResolvers.clear();
            this.identityResolvers.addAll(identityResolvers);
            return this;
        }

        /**
         * Creates the client.
         *
         * @return the created client.
         */
        public PersonDirectoryClient build() {
            return new PersonDirectoryClient(this);
        }
    }
}
