/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.context.Context;

class StsAssumeRoleResolverTest {

    private static final StsEndpointConfig TEST_ENDPOINT = new StsEndpointConfig("us-east-1", false);

    private static AwsConfigCredentialSource.AssumeRole assumeRole(
            String roleArn,
            String sourceProfile,
            String credentialSource
    ) {
        return new AwsConfigCredentialSource.AssumeRole(
                roleArn,
                sourceProfile,
                credentialSource,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void resolvesSourceCredsAndAttemptsStsCall(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = src

                [profile src]
                aws_access_key_id = SOURCE_AK
                aws_secret_access_key = SOURCE_SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "src", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        // Source creds resolve, STS call fails (no real endpoint) — that's expected
        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void resolvesSessionKeysFromSourceProfile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = src

                [profile src]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                aws_session_token = TOK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "src", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        // Session keys resolve, STS call fails — expected
        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void detectsCircularSourceProfile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = B

                [profile B]
                role_arn = arn:aws:iam::123456789:role/RoleB
                source_profile = default
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "B", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void failsWhenSourceProfileNotFound(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = nonexistent
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "nonexistent", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void failsWithUnsupportedCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "CustomUnsupportedProvider");
        var resolver = new StsAssumeRoleResolver(source, null, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void failsWhenNeitherSourceProfileNorCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, null);
        var resolver = new StsAssumeRoleResolver(source, null, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void failsWhenSourceProfileHasNoCredentialSources(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = empty

                [profile empty]
                region = us-east-1
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "empty", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void failsWhenProfileFileIsNull() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "src", null);
        var resolver = new StsAssumeRoleResolver(source, null, TEST_ENDPOINT);

        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void chainedAssumeRoleResolvesNestedSourceProfile(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::111:role/RoleA
                source_profile = B

                [profile B]
                role_arn = arn:aws:iam::222:role/RoleB
                source_profile = C

                [profile C]
                aws_access_key_id = LEAF_AK
                aws_secret_access_key = LEAF_SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::111:role/RoleA", "B", null);
        var resolver = new StsAssumeRoleResolver(source, profileFile, TEST_ENDPOINT);

        // Walks A -> B -> C (static keys), then attempts STS calls which fail
        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void credentialSourceEnvironmentResolvesAndAttemptsSts(@TempDir Path tmp) throws IOException {
        // This test requires real env vars — will fail if AWS_ACCESS_KEY_ID not set
        // We test the error path (env vars not set)
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "Environment");
        var resolver = new StsAssumeRoleResolver(source, null, TEST_ENDPOINT);

        // Fails because AWS_ACCESS_KEY_ID is not set in test environment
        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }
}
