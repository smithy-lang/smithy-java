/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.provider;

import java.net.URI;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.smithy.awsmcp.model.AwsServiceMetadata;
import software.amazon.smithy.awsmcp.model.PreRequest;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.CallHook;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.modelbundle.api.BundlePlugin;
import software.amazon.smithy.modelbundle.api.StaticAuthSchemeResolver;

final class AwsServiceBundle implements BundlePlugin {
    private static final Set<ShapeId> GOOD_PROTOCOLS = Set.of(
            "aws.protocols#restJson1",
            "aws.protocols#awsJson1_0",
            "aws.protocols#awsJson1_1",
            "aws.protocols#restXml",
            "smithy.protocols#rpcv2Cbor")
            .stream()
            .map(ShapeId::from)
            .collect(Collectors.toSet());

    private static final ClientProtocolFactory<?> AWS_JSON_1 = findAwsJson();
    private static ClientProtocolFactory<?> findAwsJson() {
        var awsJson1 = ShapeId.from("aws.protocols#awsJson1_0");
        for (var protocolImpl : ServiceLoader.load(ClientProtocolFactory.class)) {
            if (protocolImpl.id().equals(awsJson1)) {
                return protocolImpl;
            }
        }
        throw new RuntimeException("Couldn't find AWS-JSON 1.0 implementation");
    }

    private final AwsServiceMetadata serviceMetadata;
    private final AuthScheme<?, ?> authScheme;

    AwsServiceBundle(AwsServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
        this.authScheme = new SigV4AuthScheme(serviceMetadata.getSigv4SigningName());
    }

    @Override
    public <C extends Client, B extends Client.Builder<C, B>> B configureClient(B clientBuilder) {
        clientBuilder.addInterceptor(new AwsServiceClientInterceptor(serviceMetadata, authScheme));
        clientBuilder.endpointResolver(EndpointResolver.staticEndpoint("http://localhost"));
        clientBuilder.authSchemeResolver(StaticAuthSchemeResolver.getInstance());
        clientBuilder.putSupportedAuthSchemes(StaticAuthSchemeResolver.staticScheme(authScheme));
        if (clientBuilder instanceof DynamicClient.Builder dynamicBuilder) {
            var index = ServiceIndex.of(dynamicBuilder.model());
            var protocols = index.getProtocols(dynamicBuilder.service());
            if (protocols.keySet().stream().noneMatch(GOOD_PROTOCOLS::contains)) {
                // TODO: implement better protocol fallback behavior.
                // If we don't have the service's supported protocol, let's just
                // try AWS/JSON 1.0 and hope it works.
                var settings = ProtocolSettings.builder().service(dynamicBuilder.service()).build();
                dynamicBuilder.protocol(AWS_JSON_1.createProtocol(settings, null));
            }
        }
        return clientBuilder;
    }

    private record AwsServiceClientInterceptor(AwsServiceMetadata serviceMetadata, AuthScheme authScheme)
            implements ClientInterceptor {

        @Override
        public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
            if (!(hook.input() instanceof Document d)) {
                throw new IllegalArgumentException("Input must be a Document");
            }
            var input = d.asShape(PreRequest.builder());

            var endpoint = URI.create(Objects.requireNonNull(serviceMetadata.getEndpoints().get(input.getAwsRegion()),
                    "no endpoint for region " + input.getAwsRegion()));

            var identityResolver =
                    new SdkCredentialsResolver(ProfileCredentialsProvider.create(input.getAwsProfileName()));

            return hook.config()
                    .withRequestOverride(RequestOverrideConfig.builder()
                            .putConfig(RegionSetting.REGION, input.getAwsRegion())
                            .endpointResolver(EndpointResolver.staticEndpoint(endpoint))
                            .addIdentityResolver(identityResolver)
                            .build());
        }
    }
}
