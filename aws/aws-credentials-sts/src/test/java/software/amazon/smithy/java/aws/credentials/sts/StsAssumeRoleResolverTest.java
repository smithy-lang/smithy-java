/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.SourceIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.shapes.ShapeId;

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
        var resolver = resolver(source, profileFile, name -> null);

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
        var resolver = resolver(source, profileFile, name -> null);

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
        assertThrows(RuntimeException.class, () -> resolver(source, profileFile, name -> null));
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
        assertThrows(RuntimeException.class, () -> resolver(source, profileFile, name -> null));
    }

    @Test
    void failsWithUnsupportedCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "CustomUnsupportedProvider");
        assertThrows(RuntimeException.class, () -> resolver(source, null, name -> null));
    }

    @Test
    void failsWhenNeitherSourceProfileNorCredentialSource() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, null);
        assertThrows(RuntimeException.class, () -> resolver(source, null, name -> null));
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
        assertThrows(RuntimeException.class, () -> resolver(source, profileFile, name -> null));
    }

    @Test
    void failsWhenProfileFileIsNull() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "src", null);
        assertThrows(RuntimeException.class, () -> resolver(source, null, name -> null));
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
        var resolver = resolver(source, profileFile, name -> null);

        // Walks A -> B -> C (static keys), then attempts STS calls which fail
        assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
    }

    @Test
    void retainsAndCachesNestedAssumeRoleSource(@TempDir Path tmp) throws IOException {
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
        var resolver = resolver(source, profileFile, name -> null);

        var nestedSource = resolver.sourceResolver();
        assertSame(nestedSource, resolver.sourceResolver());
        assertInstanceOf(CachingIdentityResolver.class, nestedSource);
        resolver.close();
    }

    @Test
    void credentialSourceEnvironmentUsesConfiguredEnvironment() {
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", null, "Environment");
        var environment = Map.of(
                "AWS_ACCESS_KEY_ID",
                "CONFIGURED_AK",
                "AWS_SECRET_ACCESS_KEY",
                "CONFIGURED_SK",
                "AWS_SESSION_TOKEN",
                "CONFIGURED_TOKEN",
                "AWS_ACCOUNT_ID",
                "123456789012");
        var resolver = resolver(source, null, environment::get);

        var identity = resolver.sourceResolver().resolveIdentity(Context.empty()).identity();

        assertEquals("CONFIGURED_AK", identity.accessKeyId());
        assertEquals("CONFIGURED_TOKEN", identity.sessionToken());
        assertEquals("123456789012", identity.accountId());
    }

    @Test
    void selfReferencingProfileUsesItsOwnKeys(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = default
                aws_access_key_id = SELF_AK
                aws_secret_access_key = SELF_SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "default", null);
        var resolver = resolver(source, profileFile, name -> null);

        var identity = resolver.sourceResolver().resolveIdentity(Context.empty()).identity();
        assertEquals("SELF_AK", identity.accessKeyId());
    }

    @Test
    void selfReferencingProfileRequiresCompleteKeys(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::123456789:role/RoleA
                source_profile = default
                aws_access_key_id = PARTIAL_AK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::123456789:role/RoleA", "default", null);

        var error = assertThrows(IllegalStateException.class, () -> resolver(source, profileFile, name -> null));
        assertTrue(error.getMessage().contains("complete static or session credentials"));
    }

    @Test
    void nestedSourceProfileKeysTakePriorityOverItsRole(@TempDir Path tmp) throws IOException {
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                role_arn = arn:aws:iam::111:role/RoleA
                source_profile = B

                [profile B]
                role_arn = arn:aws:iam::222:role/RoleB
                source_profile = C
                aws_access_key_id = B_AK
                aws_secret_access_key = B_SK

                [profile C]
                aws_access_key_id = C_AK
                aws_secret_access_key = C_SK
                """);

        var profileFile = AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
        var source = assumeRole("arn:aws:iam::111:role/RoleA", "B", null);
        var resolver = resolver(source, profileFile, name -> null);

        var identity = resolver.sourceResolver().resolveIdentity(Context.empty()).identity();
        assertEquals("B_AK", identity.accessKeyId());
    }

    @Test
    void sourceCredentialSnapshotForwardsInvalidation() {
        var invalidations = new AtomicInteger();
        IdentityResolver<AwsCredentialsIdentity> retainedResolver = new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
                throw new AssertionError("Snapshot resolver must not resolve the retained resolver again");
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }

            @Override
            public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
                invalidations.incrementAndGet();
            }
        };
        var credentials = AwsCredentialsIdentity.create("AKID", "SECRET");
        var snapshot = StsAssumeRoleResolver.createSourceResolver(credentials, retainedResolver);

        assertSame(credentials, snapshot.resolveIdentity(Context.empty()).identity());
        snapshot.invalidate(credentials);
        assertEquals(1, invalidations.get());
    }

    @Test
    void signedStsClientInstallsCredentialInvalidationInterceptor() {
        var credentials = AwsCredentialsIdentity.create("AKID", "SECRET");

        try (var client = StsClientFactory.create(IdentityResolver.of(credentials), TEST_ENDPOINT)) {
            assertTrue(client.config()
                    .supportedAuthSchemes()
                    .stream()
                    .anyMatch(scheme -> scheme.schemeId().equals(ShapeId.from("aws.auth#sigv4"))));
            assertTrue(client.config()
                    .interceptors()
                    .stream()
                    .anyMatch(interceptor -> interceptor.getClass()
                            .getSimpleName()
                            .equals("InvalidateCredentialsInterceptor")));
        }
    }

    private static StsAssumeRoleResolver resolver(
            AwsConfigCredentialSource.AssumeRole source,
            AwsProfileFile profileFile,
            Function<String, String> environment
    ) {
        var setup = ChainSetup.builder().profileFile(profileFile).env(environment).build();
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            setup.setProfile(profileFile.profile("default"));
        }
        setup.setProviders(List.of(environmentSourceProvider()));
        var sourceResolver = ProfileSourceResolver.resolve(source, "default", setup);
        return new StsAssumeRoleResolver(source, TEST_ENDPOINT, sourceResolver);
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
                String accessKey = setup.getenv("AWS_ACCESS_KEY_ID");
                String secretKey = setup.getenv("AWS_SECRET_ACCESS_KEY");
                if (accessKey == null || secretKey == null) {
                    throw new IllegalStateException("Missing environment credentials");
                }
                return IdentityResolver.of(AwsCredentialsIdentity.create(
                        accessKey,
                        secretKey,
                        setup.getenv("AWS_SESSION_TOKEN"),
                        null,
                        setup.getenv("AWS_ACCOUNT_ID")));
            }
        };
    }
}
