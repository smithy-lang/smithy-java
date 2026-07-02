/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.Objects;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.awsquery.AwsQueryClientProtocol;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.StsEndpointSettings;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Creates STS dynamic clients for credential resolution.
 *
 * <p>Uses the bundled STS Smithy model. Endpoint resolution is handled by the
 * auto-discovered EndpointRulesPlugin using the region and STS_USE_GLOBAL_ENDPOINT
 * values supplied via {@link StsEndpointConfig}.
 */
final class StsClientFactory {

    static final ShapeId STS_SERVICE = ShapeId.from("com.amazonaws.sts#AWSSecurityTokenServiceV20110615");

    private static final String STS_VERSION = "2011-06-15";

    /**
     * Creates an STS client configured with the given source credentials.
     */
    static DynamicClient create(
            IdentityResolver<AwsCredentialsIdentity> sourceCredentials,
            StsEndpointConfig endpoint
    ) {
        return baseBuilder(endpoint).addIdentityResolver(sourceCredentials).build();
    }

    /**
     * Creates an STS client with no auth (for operations like AssumeRoleWithWebIdentity
     * where the token itself is the authentication).
     */
    static DynamicClient createNoAuth(StsEndpointConfig endpoint) {
        return baseBuilder(endpoint).authSchemeResolver(AuthSchemeResolver.NO_AUTH).build();
    }

    private static DynamicClient.Builder baseBuilder(StsEndpointConfig endpoint) {
        var builder = DynamicClient.builder()
                .model(model())
                .serviceId(STS_SERVICE)
                .protocol(new AwsQueryClientProtocol(STS_SERVICE, STS_VERSION));
        if (endpoint.region() != null) {
            builder.putConfig(RegionSetting.REGION, endpoint.region());
        }
        builder.putConfig(StsEndpointSettings.STS_USE_GLOBAL_ENDPOINT, endpoint.useGlobalEndpoint());
        return builder;
    }

    static Model model() {
        return ModelHolder.MODEL;
    }

    private static final class ModelHolder {
        static final Model MODEL = Model.assembler()
                .discoverModels(StsClientFactory.class.getClassLoader())
                .addImport(Objects.requireNonNull(
                        StsClientFactory.class.getResource("sts-2011-06-15.json"),
                        "Bundled STS model resource not found"))
                .assemble()
                .unwrap();
    }

    private StsClientFactory() {}
}
