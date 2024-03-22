/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import software.amazon.smithy.java.runtime.api.EndpointProvider;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeOption;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.runtime.client.core.CallPipeline;
import software.amazon.smithy.java.runtime.client.core.ClientCall;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.example.model.GetPersonImage;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.GetPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PersonDirectory;
import software.amazon.smithy.java.runtime.example.model.PutPerson;
import software.amazon.smithy.java.runtime.example.model.PutPersonImage;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonImageOutput;
import software.amazon.smithy.java.runtime.example.model.PutPersonInput;
import software.amazon.smithy.java.runtime.example.model.PutPersonOutput;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyBuilder;

// Example of a potentially generated client.
public final class PersonDirectoryClient implements PersonDirectory {

    private final EndpointProvider endpointProvider;
    private final CallPipeline<?, ?> pipeline;
    private final TypeRegistry typeRegistry;
    private final ClientInterceptor interceptor;

    private PersonDirectoryClient(Builder builder) {
        this.endpointProvider = Objects.requireNonNull(builder.endpointProvider, "endpointProvider is null");
        this.pipeline = new CallPipeline<>(Objects.requireNonNull(builder.protocol, "protocol is null"));
        this.interceptor = builder.interceptor;
        // Here is where you would register errors bound to the service on the registry.
        // ...
        this.typeRegistry = TypeRegistry.builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public PutPersonOutput putPerson(PutPersonInput input, Context context) {
        return call(input, null, null, new PutPerson(), context);
    }

    @Override
    public PutPersonImageOutput putPersonImage(PutPersonImageInput input, Context context) {
        return call(input, input.image(), null, new PutPersonImage(), context);
    }

    @Override
    public GetPersonImageOutput getPersonImage(GetPersonImageInput input, Context context) {
        return call(input, null, null, new GetPersonImage(), context);
    }

    /**
     * Performs the actual RPC call.
     *
     * @param input       Input to send.
     * @param inputStream Any kind of data stream extracted from the input, or null.
     * @param eventStream The event stream extracted from the input, or null. TODO: Implement.
     * @param operation   The operation shape.
     * @param context     Context of the call.
     * @return Returns the deserialized output.
     * @param <I> Input shape.
     * @param <O> Output shape.
     */
    private <I extends SerializableShape, O extends SerializableShape> O call(
            I input, DataStream inputStream, Object eventStream, SdkOperation<I, O> operation, Context context) {
        // Create a copy of the type registry that adds the errors this operation can encounter.
        TypeRegistry operationRegistry = TypeRegistry.builder()
                .putAllTypes(typeRegistry, operation.typeRegistry())
                .build();

        // TODO: this is just to test.
        IdentityResolvers identityResolvers = new IdentityResolvers() {
            @SuppressWarnings("unchecked")
            @Override
            public <T extends Identity> IdentityResolver<T> identityResolver(Class<T> identityType) {
                if (identityType.equals(TokenIdentity.class)) {
                    return (IdentityResolver<T>) new TokenResolver();
                } else {
                    return null;
                }
            }
        };

        // TODO: Just for testing.
        var supportedAuthScheme = new AuthScheme<SmithyHttpRequest, TokenIdentity>() {
            @Override
            public String schemeId() {
                return "smithy.api#bearerAuth";
            }

            @Override
            public Class<SmithyHttpRequest> requestType() {
                return SmithyHttpRequest.class;
            }

            @Override
            public Class<TokenIdentity> identityType() {
                return TokenIdentity.class;
            }

            @Override
            public Optional<IdentityResolver<TokenIdentity>> identityResolver(IdentityResolvers resolvers) {
                return Optional.ofNullable(resolvers.identityResolver(TokenIdentity.class));
            }

            @Override
            public Signer<SmithyHttpRequest, TokenIdentity> signer() {
                return (request, tokenIdentity, properties) -> request;
            }
        };

        // TODO: for testing.
        var authSchemeResolver = new AuthSchemeResolver() {
            @Override
            public List<AuthSchemeOption> resolveAuthScheme(Params params) {
                return List.of(
                        new AuthSchemeOption() {
                            @Override
                            public String schemeId() {
                                return "smithy.api#bearerAuth";
                            }

                            @Override
                            public AuthProperties identityProperties() {
                                return AuthProperties.builder().build();
                            }

                            @Override
                            public AuthProperties signerProperties() {
                                return AuthProperties.builder().build();
                            }
                        }
                );
            }
        };

        return pipeline.send(ClientCall.<I, O> builder()
                                     .input(input)
                                     .operation(operation)
                                     .endpointProvider(endpointProvider)
                                     .context(context)
                                     .requestInputStream(inputStream)
                                     .requestEventStream(eventStream)
                                     .interceptor(interceptor)
                                     .addSupportedAuthScheme(supportedAuthScheme)
                                     .authSchemeResolver(authSchemeResolver)
                                     .identityResolvers(identityResolvers)
                                     .errorCreator((c, id) -> {
                                         ShapeId shapeId = ShapeId.from(id);
                                         return operationRegistry.create(shapeId, ModeledSdkException.class);
                                     })
                                     .build());
    }

    // TODO: move this.
    private static final class TokenResolver implements IdentityResolver<TokenIdentity> {
        @Override
        public Class<TokenIdentity> identityType() {
            return TokenIdentity.class;
        }

        @Override
        public TokenIdentity resolveIdentity(AuthProperties requestProperties) {
            return TokenIdentity.create("xyz");
        }
    }

    public static final class Builder implements SmithyBuilder<PersonDirectoryClient> {

        private ClientProtocol<?, ?> protocol;
        private EndpointProvider endpointProvider;
        private ClientInterceptor interceptor;

        private Builder() {}

        /**
         * Set the protocol to use to call the service.
         *
         * @param protocol Protocol to use.
         * @return Returns the builder.
         */
        public Builder protocol(ClientProtocol<?, ?> protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Set the provider used to resolve endpoints.
         *
         * @param endpointProvider Endpoint provider to use to resolve endpoints.
         * @return Returns the endpoint provider.
         */
        public Builder endpointProvider(EndpointProvider endpointProvider) {
            this.endpointProvider = endpointProvider;
            return this;
        }

        public Builder interceptor(ClientInterceptor interceptor) {
            this.interceptor = interceptor;
            return this;
        }

        @Override
        public PersonDirectoryClient build() {
            return new PersonDirectoryClient(this);
        }
    }
}
