/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

class AwsProfileCredentialsResolverTest {

    // --- Built-in handlers (static + session) --------------------------------------------------

    @Test
    void basicCredentialsWhenNoSessionTokenOrRole(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);
        AwsCredentialsIdentity id = buildResolver(creds, "default").resolveIdentity(Context.empty()).unwrap();
        assertEquals("AK", id.accessKeyId());
        assertEquals("SK", id.secretAccessKey());
        assertNull(id.sessionToken());
    }

    @Test
    void sessionCredentialsWhenSessionTokenPresent(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                aws_session_token = TOK
                """);
        AwsCredentialsIdentity id = buildResolver(creds, "default").resolveIdentity(Context.empty()).unwrap();
        assertEquals("TOK", id.sessionToken());
    }

    @Test
    void reportsAccountIdWhenPresent(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [default]
                aws_access_key_id = K
                aws_secret_access_key = S
                aws_account_id = 123456789012
                """);
        AwsCredentialsIdentity id = buildResolver(creds, "default").resolveIdentity(Context.empty()).unwrap();
        assertEquals("123456789012", id.accountId());
    }

    // --- Handler chain semantics --------------------------------------------------------------

    @Test
    void unhandledSourceTypeYieldsTypedError(@TempDir Path tmp) throws IOException {
        // Profile defines an AssumeRole source but the default resolver has no handler for it.
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [profile role-profile]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                """, StandardCharsets.UTF_8);

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .configFile(config)
                .credentialsFile(null)
                .profileName("role-profile")
                .build();

        IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(Context.empty());
        assertNull(result.identity());
        assertNotNull(result.error());
        assertTrue(result.error().contains("AssumeRole"));
        assertTrue(result.error().contains("no handler"));
        assertEquals(AwsProfileCredentialsResolver.class, result.resolver());
    }

    @Test
    void customHandlerChainTakesOver(@TempDir Path tmp) throws IOException {
        // A bespoke handler that claims AssumeRole sources and returns a deterministic identity.
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [profile role-profile]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                """, StandardCharsets.UTF_8);

        AwsConfigCredentialSourceHandler stubAssumeRoleHandler = (source, ctx) -> {
            if (!(source instanceof AwsConfigCredentialSource.AssumeRole r)) {
                return null;
            }
            return IdentityResult.of(AwsCredentialsIdentity.create(
                    "assumed-" + r.roleArn(),
                    "assumed-secret"));
        };

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .configFile(config)
                .credentialsFile(null)
                .profileName("role-profile")
                .addHandler(stubAssumeRoleHandler)
                .addHandler(new StaticKeysHandler())
                .addHandler(new SessionKeysHandler())
                .build();

        AwsCredentialsIdentity id = resolver.resolveIdentity(Context.empty()).unwrap();
        assertEquals("assumed-arn:aws:iam::123:role/X", id.accessKeyId());
    }

    @Test
    void fallsThroughToNextSourceWhenFirstIsUnhandled(@TempDir Path tmp) throws IOException {
        // Profile declares both role_arn and static keys. With ignoreUnhandledSources(true),
        // the resolver skips the AssumeRole source (no handler) and uses the StaticKeys one.
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [profile mixed]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                aws_access_key_id = FALLBACK_AK
                aws_secret_access_key = FALLBACK_SK
                """, StandardCharsets.UTF_8);

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .configFile(config)
                .credentialsFile(null)
                .profileName("mixed")
                .ignoreUnhandledSources(true)
                .build();

        AwsCredentialsIdentity id = resolver.resolveIdentity(Context.empty()).unwrap();
        assertEquals("FALLBACK_AK", id.accessKeyId());
        assertEquals("FALLBACK_SK", id.secretAccessKey());
    }

    @Test
    void unhandledSourceFailsByDefault(@TempDir Path tmp) throws IOException {
        // By default (strict SEP mode), an unhandled high-priority source is an error.
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [profile mixed]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                aws_access_key_id = FALLBACK_AK
                aws_secret_access_key = FALLBACK_SK
                """, StandardCharsets.UTF_8);

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .configFile(config)
                .credentialsFile(null)
                .profileName("mixed")
                .build();

        IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(Context.empty());
        assertNull(result.identity());
        assertTrue(result.error().contains("AssumeRole"));
    }

    @Test
    void profileWithoutRecognizedSourcesErrors(@TempDir Path tmp) throws IOException {
        // A profile that only sets region has no credential source.
        Path config = tmp.resolve("config");
        Files.writeString(config, """
                [default]
                region = us-east-1
                """, StandardCharsets.UTF_8);

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .configFile(config)
                .credentialsFile(null)
                .profileName("default")
                .build();

        IdentityResult<AwsCredentialsIdentity> result = resolver.resolveIdentity(Context.empty());
        assertNull(result.identity());
        assertTrue(result.error().contains("does not describe any credential source"));
    }

    // --- Existing behaviors ---------------------------------------------------------------------

    @Test
    void missingProfileReturnsErrorResult(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [default]
                aws_access_key_id = K
                aws_secret_access_key = S
                """);
        IdentityResult<AwsCredentialsIdentity> result = buildResolver(creds, "not-there")
                .resolveIdentity(Context.empty());
        assertNull(result.identity());
        assertTrue(result.error().contains("not-there"));
    }

    @Test
    void refreshReloadsCredentialsFromDisk(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [default]
                aws_access_key_id = V1
                aws_secret_access_key = S1
                """);

        AwsProfileCredentialsResolver resolver = buildResolver(creds, "default");
        assertEquals("V1", resolver.resolveIdentity(Context.empty()).unwrap().accessKeyId());

        Files.writeString(creds, """
                [default]
                aws_access_key_id = V2
                aws_secret_access_key = S2
                """, StandardCharsets.UTF_8);

        assertEquals("V1", resolver.resolveIdentity(Context.empty()).unwrap().accessKeyId());
        resolver.refresh();
        assertEquals("V2", resolver.resolveIdentity(Context.empty()).unwrap().accessKeyId());
    }

    @Test
    void canUsePreloadedProfileFile(@TempDir Path tmp) throws IOException {
        Path creds = writeCredentials(tmp, """
                [prod]
                aws_access_key_id = PK
                aws_secret_access_key = PS
                """);

        AwsProfileFile file = AwsProfileFile.builder()
                .configFile(null)
                .credentialsFile(creds)
                .build();

        AwsProfileCredentialsResolver resolver = AwsProfileCredentialsResolver.builder()
                .profileFile(file)
                .profileName("prod")
                .build();

        assertEquals("PK", resolver.resolveIdentity(Context.empty()).unwrap().accessKeyId());
    }

    private static AwsProfileCredentialsResolver buildResolver(Path credentials, String profile) {
        return AwsProfileCredentialsResolver.builder()
                .configFile(null)
                .credentialsFile(credentials)
                .profileName(profile)
                .build();
    }

    private static Path writeCredentials(Path tmp, String content) throws IOException {
        Path p = tmp.resolve("credentials");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }
}
