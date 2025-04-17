/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.awsmcp.model.PreRequest;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolverParams;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.modelbundle.api.BundleClientPluginProvider;
import software.amazon.smithy.modelbundle.api.StaticAuthSchemePlugin;

final class AwsAuthProvider implements BundleClientPluginProvider {
    private final AwsServiceMetadata serviceMetadata;
    private final AwsServicePlugin plugin;

    AwsAuthProvider(AwsServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
        this.plugin = new AwsServicePlugin();
    }

    @Override
    public ClientPlugin newPlugin() {
        return config -> config.applyPlugin(plugin);
    }

    @SuppressWarnings("rawtypes")
    private final class AwsServicePlugin
            implements ClientPlugin, ClientInterceptor, EndpointResolver, IdentityResolver<AwsCredentialsIdentity>,
            AuthScheme {
        private static final Context.Key<PreRequest> PRE_REQUEST = Context.key("pre request");
        private final AuthScheme<?, ?> authScheme = new SigV4AuthScheme(serviceMetadata.getSigv4SigningName());

        @Override
        public void configureClient(ClientConfig.Builder config) {
            config.applyPlugin(new StaticAuthSchemePlugin(this))
                    .addIdentityResolver(this)
                    .endpointResolver(this)
                    .addInterceptor(this);
        }

        @Override
        public void readBeforeExecution(InputHook<?, ?> hook) {
            var input = ((Document) hook.input()).asShape(PreRequest.builder());
            hook.context().put(PRE_REQUEST, input);
            hook.context().put(RegionSetting.REGION, input.getAwsRegion());
        }

        @Override
        public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
            var input = params.context().expect(PRE_REQUEST);
            var endpoint = URI.create(Objects.requireNonNull(serviceMetadata.getEndpoints().get(input.getAwsRegion()),
                    "no endpoint for region " + input.getAwsRegion()));
            return CompletableFuture.completedFuture(Endpoint.builder()
                    .uri(endpoint)
                    .build());
        }

        @Override
        public CompletableFuture<IdentityResult<AwsCredentialsIdentity>> resolveIdentity(Context requestProperties) {
            var input = requestProperties.expect(PRE_REQUEST);
            return new SdkCredentialsResolver(ProfileCredentialsProvider.create(input.getAwsProfileName()))
                    .resolveIdentity(requestProperties);
        }

        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public ShapeId schemeId() {
            return authScheme.schemeId();
        }

        @Override
        public Class requestClass() {
            return authScheme.requestClass();
        }

        @Override
        public Class identityClass() {
            return authScheme.identityClass();
        }

        @Override
        public Signer signer() {
            return authScheme.signer();
        }

        @Override
        public IdentityResolver identityResolver(IdentityResolvers resolvers) {
            return authScheme.identityResolver(resolvers);
        }

        @Override
        public Context getSignerProperties(Context context) {
            return addPreRequest(authScheme.getSignerProperties(context), context);
        }

        @Override
        public Context getIdentityProperties(Context context) {
            return addPreRequest(authScheme.getIdentityProperties(context), context);
        }

        // all of this is to work around the fact the auth scheme option is given a copied and filtered
        // view of the request context. sadly, there is currently no way to either set the auth scheme
        // at a request level from a client plugin or to access the full, unfiltered request context within
        // an auth scheme.
        private Context addPreRequest(Context context, Context base) {
            // just restore everything, disregard the filtering
            return Context.unmodifiableCopy(Context.modifiableCopy(context).merge(base));
        }
    }
}
