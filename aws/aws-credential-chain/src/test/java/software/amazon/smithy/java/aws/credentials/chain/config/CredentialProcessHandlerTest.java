/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

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
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.context.Context;

class CredentialProcessHandlerTest {

    @Test
    void successfulProcessReturnsCredentials(@TempDir Path tmp) throws IOException {
        Path script = writeScript(tmp,
                """
                        #!/bin/sh
                        echo '{"Version": 1, "AccessKeyId": "AKIA_PROC", "SecretAccessKey": "SECRET_PROC", "SessionToken": "TOK", "AccountId": "123456789012"}'
                        """);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        IdentityResult<AwsCredentialsIdentity> result = createFromProfileResult(source);

        assertNotNull(result);
        AwsCredentialsIdentity id = result.unwrap();
        assertEquals("AKIA_PROC", id.accessKeyId());
        assertEquals("SECRET_PROC", id.secretAccessKey());
        assertEquals("TOK", id.sessionToken());
        assertEquals("123456789012", id.accountId());
    }

    @Test
    void processWithExpirationParsesTimestamp(@TempDir Path tmp) throws IOException {
        Path script = writeScript(tmp,
                """
                        #!/bin/sh
                        echo '{"Version": 1, "AccessKeyId": "AK", "SecretAccessKey": "SK", "Expiration": "2099-01-01T00:00:00Z"}'
                        """);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        AwsCredentialsIdentity id = createFromProfileResult(source).unwrap();
        assertNotNull(id.expirationTime());
        assertEquals("2099-01-01T00:00:00Z", id.expirationTime().toString());
    }

    @Test
    void processWithoutSessionTokenReturnsBasicCredentials(@TempDir Path tmp) throws IOException {
        Path script = writeScript(tmp, """
                #!/bin/sh
                echo '{"Version": 1, "AccessKeyId": "AK", "SecretAccessKey": "SK"}'
                """);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        AwsCredentialsIdentity id = createFromProfileResult(source).unwrap();
        assertEquals("AK", id.accessKeyId());
        assertEquals("SK", id.secretAccessKey());
        assertNull(id.sessionToken());
    }

    @Test
    void nonZeroExitCodeReturnsError(@TempDir Path tmp) throws IOException {
        Path script = writeScript(tmp, """
                #!/bin/sh
                echo "Something went wrong" >&2
                exit 1
                """);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        IdentityResult<AwsCredentialsIdentity> result = createFromProfileResult(source);

        assertNotNull(result);
        assertNull(result.identity());
        assertTrue(result.error().contains("Something went wrong"));
    }

    @Test
    void missingRequiredFieldsReturnsError(@TempDir Path tmp) throws IOException {
        Path script = writeScript(tmp, """
                #!/bin/sh
                echo '{"Version": 1, "AccessKeyId": "AK"}'
                """);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        IdentityResult<AwsCredentialsIdentity> result = createFromProfileResult(source);

        assertNull(result.identity());
        assertTrue(result.error().contains("SecretAccessKey"));
    }

    @Test
    void returnsNullForNonCredentialProcessSource() {
        AwsConfigCredentialSource.StaticKeys other = new AwsConfigCredentialSource.StaticKeys("AK", "SK", null);
        assertNull(createFromProfileResult(other));
    }

    private static Path writeScript(Path tmp, String content) throws IOException {
        Path script = tmp.resolve("cred-proc.sh");
        Files.writeString(script, content, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }

    private IdentityResult<AwsCredentialsIdentity> createFromProfileResult(AwsConfigCredentialSource source) {
        if (!(source instanceof AwsConfigCredentialSource.CredentialProcess cp)) {
            return null;
        }
        var handler = new CredentialProcessHandler();
        var setup = new ChainSetup(null);
        try {
            Path configPath = Files.createTempFile("aws-config", ".ini");
            Files.writeString(configPath, "[default]\ncredential_process=" + cp.commandLine() + "\n");
            var file = AwsProfileFile.builder().configFile(configPath).credentialsFile(null).build();
            setup.setProfileFile(file);
            setup.setProfile(file.activeProfile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        setup.setCurrentProvider(handler);
        handler.create(AwsCredentialsIdentity.class, setup);
        var resolvers = setup.resolvers();
        if (resolvers.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        var r = (IdentityResolver<AwsCredentialsIdentity>) resolvers.getFirst().resolver();
        return r.resolveIdentity(Context.empty());
    }
}
