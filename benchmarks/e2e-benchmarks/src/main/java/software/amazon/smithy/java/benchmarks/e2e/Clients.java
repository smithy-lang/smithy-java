/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.imds.ImdsCredentialProvider;
import software.amazon.smithy.java.benchmarks.e2e.dynamodb.client.DynamoDBClient;
import software.amazon.smithy.java.benchmarks.e2e.s3.client.S3Client;

/**
 * Constructs the smithy-java-generated DynamoDB and S3 clients used by the
 * benchmark. Region comes from the workload's actionConfig; credentials come
 * directly from the EC2 IMDSv2 endpoint.
 *
 * <p>Why we bypass the standard {@code CredentialChain}: the
 * {@code SystemPropertiesCredentialProvider} and
 * {@code EnvironmentCredentialProvider} both call
 * {@code ChainSetup.addTerminalResolver} unconditionally during assembly,
 * which stops the chain after the first provider regardless of whether
 * credentials are actually present. That makes IMDS unreachable on EC2
 * instances. The benchmark always runs on EC2, so wiring IMDS directly is
 * both correct and avoids the bug.
 */
final class Clients {

    private static final IdentityResolver<AwsCredentialsIdentity> IMDS = buildImds();

    private Clients() {}

    static DynamoDBClient dynamodb(String region) {
        return DynamoDBClient.builder()
                .putConfig(RegionSetting.REGION, region)
                .addIdentityResolver(IMDS)
                .build();
    }

    static S3Client s3(String region) {
        return S3Client.builder()
                .putConfig(RegionSetting.REGION, region)
                .addIdentityResolver(IMDS)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static IdentityResolver<AwsCredentialsIdentity> buildImds() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "e2e-imds-refresh");
            t.setDaemon(true);
            return t;
        });
        var provider = new ImdsCredentialProvider();
        var setup = ChainSetup.builder().executor(executor).build();
        setup.setCurrentProvider(provider);
        provider.setup(AwsCredentialsIdentity.class, setup);
        var resolvers = setup.resolvers();
        if (resolvers.isEmpty()) {
            throw new IllegalStateException("IMDS provider did not register a resolver");
        }
        return (IdentityResolver<AwsCredentialsIdentity>) resolvers.get(0).resolver();
    }
}
