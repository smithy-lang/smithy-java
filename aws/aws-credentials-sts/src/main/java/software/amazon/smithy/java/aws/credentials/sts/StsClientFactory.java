/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.awsquery.AwsQueryClientProtocol;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Creates STS dynamic clients for credential resolution.
 *
 * <p>Uses the bundled STS Smithy model. Endpoint resolution is handled by the
 * auto-discovered EndpointRulesPlugin.
 */
final class StsClientFactory {

    static final ShapeId STS_SERVICE = ShapeId.from("com.amazonaws.sts#AWSSecurityTokenServiceV20110615");

    private static final String STS_VERSION = "2011-06-15";
    private static final String DEFAULT_ENDPOINT = "https://sts.amazonaws.com";

    /**
     * Creates an STS client configured with the given source credentials.
     */
    static DynamicClient create(IdentityResolver<AwsCredentialsIdentity> sourceCredentials) {
        return DynamicClient.builder()
                .model(model())
                .serviceId(STS_SERVICE)
                .protocol(new AwsQueryClientProtocol(STS_SERVICE, STS_VERSION))
                .addIdentityResolver(sourceCredentials)
                .endpointResolver(EndpointResolver.staticEndpoint(DEFAULT_ENDPOINT))
                .build();
    }

    /**
     * Creates an STS client with no auth (for operations like AssumeRoleWithWebIdentity
     * where the token itself is the authentication).
     */
    static DynamicClient createNoAuth() {
        return DynamicClient.builder()
                .model(model())
                .serviceId(STS_SERVICE)
                .protocol(new AwsQueryClientProtocol(STS_SERVICE, STS_VERSION))
                .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
                .endpointResolver(EndpointResolver.staticEndpoint(DEFAULT_ENDPOINT))
                .build();
    }

    static Model model() {
        return ModelHolder.MODEL;
    }

    private static final class ModelHolder {
        static final Model MODEL = Model.assembler()
                .discoverModels(StsClientFactory.class.getClassLoader())
                .assemble()
                .unwrap();
    }

    private StsClientFactory() {}
}
