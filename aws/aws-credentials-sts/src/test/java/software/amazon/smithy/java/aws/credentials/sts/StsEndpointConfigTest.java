/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;

class StsEndpointConfigTest {

    @Test
    void sourceRegionWinsOverEverything(@TempDir Path tmp) throws IOException {
        var setup = setupWith(Map.of("AWS_REGION", "us-west-2"), profileWith(tmp, "region = eu-west-1"));
        var cfg = StsEndpointConfig.resolve("ap-south-1", setup);
        assertEquals("ap-south-1", cfg.region());
        assertFalse(cfg.useGlobalEndpoint());
    }

    @Test
    void awsRegionEnvWinsOverDefaultRegionAndProfile(@TempDir Path tmp) throws IOException {
        var setup = setupWith(
                Map.of("AWS_REGION", "us-west-2", "AWS_DEFAULT_REGION", "us-east-2"),
                profileWith(tmp, "region = eu-west-1"));
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertEquals("us-west-2", cfg.region());
    }

    @Test
    void awsDefaultRegionEnvUsedWhenAwsRegionMissing(@TempDir Path tmp) throws IOException {
        var setup = setupWith(Map.of("AWS_DEFAULT_REGION", "us-east-2"), profileWith(tmp, "region = eu-west-1"));
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertEquals("us-east-2", cfg.region());
    }

    @Test
    void profileRegionUsedWhenEnvMissing(@TempDir Path tmp) throws IOException {
        var setup = setupWith(Map.of(), profileWith(tmp, "region = eu-west-1"));
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertEquals("eu-west-1", cfg.region());
    }

    @Test
    void defaultsToUsEast1WithGlobalEndpointWhenNothingResolves() {
        var setup = setupWith(Map.of(), null);
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertEquals("us-east-1", cfg.region());
        assertTrue(cfg.useGlobalEndpoint());
    }

    @Test
    void legacyEnvForcesGlobalEndpoint(@TempDir Path tmp) throws IOException {
        var setup = setupWith(
                Map.of("AWS_REGION", "us-west-2", "AWS_STS_REGIONAL_ENDPOINTS", "legacy"),
                null);
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertEquals("us-west-2", cfg.region());
        assertTrue(cfg.useGlobalEndpoint());
    }

    @Test
    void regionalEnvKeepsRegionalEndpoint(@TempDir Path tmp) throws IOException {
        var setup = setupWith(
                Map.of("AWS_REGION", "us-west-2", "AWS_STS_REGIONAL_ENDPOINTS", "regional"),
                null);
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertFalse(cfg.useGlobalEndpoint());
    }

    @Test
    void profileStsRegionalEndpointsUsedWhenEnvMissing(@TempDir Path tmp) throws IOException {
        var setup = setupWith(
                Map.of("AWS_REGION", "us-west-2"),
                profileWith(tmp, "sts_regional_endpoints = legacy"));
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertTrue(cfg.useGlobalEndpoint());
    }

    @Test
    void envOverridesProfileStsRegionalEndpoints(@TempDir Path tmp) throws IOException {
        var setup = setupWith(
                Map.of("AWS_REGION", "us-west-2", "AWS_STS_REGIONAL_ENDPOINTS", "regional"),
                profileWith(tmp, "sts_regional_endpoints = legacy"));
        var cfg = StsEndpointConfig.resolve(null, setup);
        assertFalse(cfg.useGlobalEndpoint());
    }

    private static ChainSetup setupWith(Map<String, String> env, AwsProfileFile profileFile) {
        Map<String, String> envCopy = new HashMap<>(env);
        var setup = ChainSetup.builder().env(envCopy::get).build();
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            setup.setProfile(profileFile.activeProfile(k -> null));
        }
        return setup;
    }

    private static AwsProfileFile profileWith(Path tmp, String body) throws IOException {
        Path config = tmp.resolve("config-" + body.hashCode());
        Files.writeString(config, "[default]\n" + body + "\n");
        return AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
    }
}
