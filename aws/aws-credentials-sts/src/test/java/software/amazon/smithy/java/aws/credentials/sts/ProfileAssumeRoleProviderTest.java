/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.context.Context;

class ProfileAssumeRoleProviderTest {

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
    void registersWhenProfileHasRoleArn(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = creds

                [profile creds]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile(k -> null));
        var provider = new ProfileAssumeRoleProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(1, setup.resolvers().size());
    }

    @Test
    void skipsWhenNoRoleArn(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder().build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile(k -> null));
        var provider = new ProfileAssumeRoleProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);
        assertEquals(0, setup.resolvers().size());
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

        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("Circular") || ex.getCause().getMessage().contains("Circular"));
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

        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("nonexistent") || ex.getCause().getMessage().contains("nonexistent"));
    }

    @Test
    void failsWithUnsupportedCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "CustomUnsupportedProvider");
        var resolver = new StsAssumeRoleResolver(source, null, TEST_ENDPOINT);

        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("Unsupported") || ex.getCause().getMessage().contains("Unsupported"));
    }
}
