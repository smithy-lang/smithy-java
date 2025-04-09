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
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.endpoint.Endpoint;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.modelbundle.api.ConfigProvider;

final class AwsAuthProvider implements ConfigProvider<PreRequest> {
    private final AwsServiceMetadata serviceMetadata;

    AwsAuthProvider(AwsServiceMetadata serviceMetadata) {
        this.serviceMetadata = serviceMetadata;
    }

    @Override
    public ShapeId wrapperType() {
        return PreRequest.$ID;
    }

    @Override
    public PreRequest parse(Document input) {
        return input.asShape(PreRequest.builder());
    }

    @Override
    public IdentityResolver<?> identityResolver(PreRequest input) {
        return new SdkCredentialsResolver(ProfileCredentialsProvider.create(input.awsProfileName()));
    }

    @Override
    public EndpointResolver endpointResolver(PreRequest input) {
        // TODO: error reporting
        return ignore -> {
            var endpoint = URI.create(Objects.requireNonNull(serviceMetadata.endpoints().get(input.awsRegion()),
                    "no endpoint for region " + input.awsRegion()));
            return CompletableFuture.completedFuture(Endpoint.builder()
                    .uri(endpoint)
                    .build());
        };
    }

    @Override
    public AuthScheme<?, ?> authScheme(PreRequest input) {
        return new SigV4AuthScheme(serviceMetadata.sigv4SigningName());
    }

    @Override
    public RequestOverrideConfig.Builder adaptConfig(PreRequest args) {
        return ConfigProvider.super.adaptConfig(args)
                .putConfig(RegionSetting.REGION, args.awsRegion());
    }

}
