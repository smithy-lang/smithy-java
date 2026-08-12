/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.SourceIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

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
        assertEquals(
                Set.of(new CredentialFeatureId("o"), new CredentialFeatureId("i")),
                setup.resolvers().getFirst().featureIds());
    }

    @Test
    void credentialSourceUsesCredentialSourceFeatureId(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                credential_source = Environment
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var setup = ChainSetup.builder()
                .env(name -> switch (name) {
                    case "AWS_ACCESS_KEY_ID" -> "AK";
                    case "AWS_SECRET_ACCESS_KEY" -> "SK";
                    default -> null;
                })
                .build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile(k -> null));
        setup.setProviders(List.of(environmentSourceProvider()));
        var provider = new ProfileAssumeRoleProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);

        assertEquals(
                Set.of(new CredentialFeatureId("p"), new CredentialFeatureId("i")),
                setup.resolvers().getFirst().featureIds());
    }

    @Test
    void regionOverrideFlowsIntoStsEndpoint(@TempDir Path tmp) throws IOException {
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
        // No AWS_REGION/profile region; the client's region override should drive the STS endpoint.
        var setup = ChainSetup.builder().env(k -> null).regionOverride("eu-central-1").build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.activeProfile(k -> null));
        var provider = new ProfileAssumeRoleProvider();
        setup.setCurrentProvider(provider);

        provider.setup(AwsCredentialsIdentity.class, setup);

        assertEquals(1, setup.resolvers().size());
        assertInstanceOf(CachingIdentityResolver.class, setup.resolvers().get(0).resolver());
        var source = (AwsConfigCredentialSource.AssumeRole) setup.profile().credentialSources().getFirst();
        var resolver = ProfileAssumeRoleProvider.createResolver(source, setup);
        assertEquals("eu-central-1", resolver.endpoint().region());
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
        var setup = setup(profileFile);

        var ex = assertThrows(RuntimeException.class,
                () -> ProfileSourceResolver.resolve(source, "default", setup));
        assertTrue(ex.getMessage().contains("Circular"));
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
        var setup = setup(profileFile);

        var ex = assertThrows(RuntimeException.class,
                () -> ProfileSourceResolver.resolve(source, "default", setup));
        assertTrue(ex.getMessage().contains("nonexistent"));
    }

    @Test
    void failsWithUnsupportedCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "CustomUnsupportedProvider");
        var setup = ChainSetup.builder().build();

        var ex = assertThrows(RuntimeException.class,
                () -> ProfileSourceResolver.resolve(source, "default", setup));
        assertTrue(ex.getMessage().contains("CustomUnsupportedProvider"));
    }

    private static ChainSetup setup(AwsProfileFile profileFile) {
        var setup = ChainSetup.builder().profileFile(profileFile).build();
        setup.setProfileFile(profileFile);
        setup.setProfile(profileFile.profile("default"));
        return setup;
    }

    private static SourceIdentityProvider environmentSourceProvider() {
        return new SourceIdentityProvider() {
            @Override
            public String name() {
                return "Environment";
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(StandardProvider.ENVIRONMENT);
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {}

            @Override
            public IdentityResolver<?> createResolver(Class<? extends Identity> identityType, ChainSetup setup) {
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        setup.getenv("AWS_ACCESS_KEY_ID"),
                        setup.getenv("AWS_SECRET_ACCESS_KEY")));
            }
        };
    }
}
