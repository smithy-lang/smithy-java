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
        Path script = writeJsonScript(tmp,
                "{\"Version\": 1, \"AccessKeyId\": \"AKIA_PROC\", \"SecretAccessKey\": \"SECRET_PROC\", \"SessionToken\": \"TOK\", \"AccountId\": \"123456789012\"}");

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
        Path script = writeJsonScript(tmp,
                "{\"Version\": 1, \"AccessKeyId\": \"AK\", \"SecretAccessKey\": \"SK\", \"Expiration\": \"2099-01-01T00:00:00Z\"}");

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        AwsCredentialsIdentity id = createFromProfileResult(source).unwrap();
        assertNotNull(id.expirationTime());
        assertEquals("2099-01-01T00:00:00Z", id.expirationTime().toString());
    }

    @Test
    void processWithMalformedExpirationReturnsError(@TempDir Path tmp) throws IOException {
        Path script = writeJsonScript(tmp,
                "{\"Version\": 1, \"AccessKeyId\": \"AK\", \"SecretAccessKey\": \"SK\", \"Expiration\": \"invalid\"}");

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        IdentityResult<AwsCredentialsIdentity> result = createFromProfileResult(source);

        assertNull(result.identity());
        assertTrue(result.error().contains("unparseable Expiration"));
    }

    @Test
    void processWithoutSessionTokenReturnsBasicCredentials(@TempDir Path tmp) throws IOException {
        Path script = writeJsonScript(tmp, "{\"Version\": 1, \"AccessKeyId\": \"AK\", \"SecretAccessKey\": \"SK\"}");

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        AwsCredentialsIdentity id = createFromProfileResult(source).unwrap();
        assertEquals("AK", id.accessKeyId());
        assertEquals("SK", id.secretAccessKey());
        assertNull(id.sessionToken());
    }

    @Test
    void nonZeroExitCodeReturnsError(@TempDir Path tmp) throws IOException {
        Path script = writeFailingScript(tmp, "Something went wrong", 1);

        AwsConfigCredentialSource.CredentialProcess source =
                new AwsConfigCredentialSource.CredentialProcess(script.toString());
        IdentityResult<AwsCredentialsIdentity> result = createFromProfileResult(source);

        assertNotNull(result);
        assertNull(result.identity());
        assertTrue(result.error().contains("Something went wrong"));
    }

    @Test
    void missingRequiredFieldsReturnsError(@TempDir Path tmp) throws IOException {
        Path script = writeJsonScript(tmp, "{\"Version\": 1, \"AccessKeyId\": \"AK\"}");

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

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private static Path writeJsonScript(Path tmp, String json) throws IOException {
        // cmd's echo prints its argument verbatim (quotes included), so no quoting is needed there.
        return IS_WINDOWS
                ? writeScript(tmp, "@echo off\r\necho " + json + "\r\n")
                : writeScript(tmp, "#!/bin/sh\necho '" + json + "'\n");
    }

    private static Path writeFailingScript(Path tmp, String stderr, int exitCode) throws IOException {
        return IS_WINDOWS
                ? writeScript(tmp, "@echo off\r\necho " + stderr + " 1>&2\r\nexit /b " + exitCode + "\r\n")
                : writeScript(tmp, "#!/bin/sh\necho \"" + stderr + "\" >&2\nexit " + exitCode + "\n");
    }

    private static Path writeScript(Path tmp, String content) throws IOException {
        // On Windows the handler runs the command via cmd.exe, which would open a .sh file in its
        // associated editor instead of executing it.
        Path script = tmp.resolve(IS_WINDOWS ? "cred-proc.cmd" : "cred-proc.sh");
        Files.writeString(script, content, StandardCharsets.UTF_8);
        script.toFile().setExecutable(true);
        return script;
    }

    private IdentityResult<AwsCredentialsIdentity> createFromProfileResult(AwsConfigCredentialSource source) {
        if (!(source instanceof AwsConfigCredentialSource.CredentialProcess cp)) {
            return null;
        }
        var handler = new CredentialProcessHandler();
        var setup = ChainSetup.builder().build();
        try {
            Path configPath = Files.createTempFile("aws-config", ".ini");
            Files.writeString(configPath, "[default]\ncredential_process=" + cp.commandLine() + "\n");
            var file = AwsProfileFile.builder().configFile(configPath).credentialsFile(null).build();
            setup.setProfileFile(file);
            setup.setProfile(file.activeProfile(k -> null));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        setup.setCurrentProvider(handler);
        handler.setup(AwsCredentialsIdentity.class, setup);
        var resolvers = setup.resolvers();
        if (resolvers.isEmpty()) {
            return null;
        }
        @SuppressWarnings("unchecked")
        var r = (IdentityResolver<AwsCredentialsIdentity>) resolvers.getFirst().resolver();
        return r.resolveIdentity(Context.empty());
    }
}
