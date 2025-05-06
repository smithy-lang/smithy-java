/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import java.net.URI;
import java.util.Objects;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.awsmcp.model.PreRequest;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.CallHook;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.modelbundle.api.BundlePlugin;
import software.amazon.smithy.modelbundle.api.StaticAuthSchemeResolver;

final class AwsServiceBundle implements BundlePlugin {
    private final AwsServiceMetadata serviceMetadata;
    private final AuthScheme<?, ?> authScheme;

    AwsServiceBundle(AwsServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
        this.authScheme = new SigV4AuthScheme(serviceMetadata.getSigv4SigningName());
    }

    @Override
    public <C extends Client, B extends Client.Builder<C, B>> B configureClient(B clientBuilder) {
        clientBuilder.addInterceptor(new AwsServiceClientInterceptor(serviceMetadata));
        return clientBuilder;
    }

    private record AwsServiceClientInterceptor(AwsServiceMetadata serviceMetadata) implements ClientInterceptor {

        @Override
        public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
            if (!(hook.input() instanceof Document d)) {
                throw new IllegalArgumentException("Input must be a Document");
            }
            var input = d.asShape(PreRequest.builder());

            var endpoint = URI.create(Objects.requireNonNull(serviceMetadata.getEndpoints().get(input.getAwsRegion()),
                    "no endpoint for region " + input.getAwsRegion()));

            try (var sdkCredentialsProvider = ProfileCredentialsProvider.create(input.getAwsProfileName())) {
                var identityResolver = new SdkCredentialsResolver(sdkCredentialsProvider);

                return hook.config()
                        .withRequestOverride(RequestOverrideConfig.builder()
                                .putConfig(RegionSetting.REGION, input.getAwsRegion())
                                .endpointResolver(EndpointResolver.staticEndpoint(endpoint))
                                .addIdentityResolver(identityResolver)
                                .authSchemeResolver(StaticAuthSchemeResolver.getInstance())
                                .build());
            }
        }
    }
}
