/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Handles {@link AwsConfigCredentialSource.CredentialProcess} by invoking an external process and parsing its JSON
 * stdout per the <a href="https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sourcing-external.html">
 * credential_process specification</a>.
 *
 * <p>The process must write a JSON object to stdout with at minimum {@code Version} (integer 1),
 * {@code AccessKeyId}, and {@code SecretAccessKey}. Optional fields: {@code SessionToken},
 * {@code Expiration} (ISO 8601), {@code AccountId}.
 *
 * <p>A non-zero exit code is treated as an error. The process's stderr is captured for the error message but is never
 * logged above debug level to prevent leaking secrets.
 */
public final class CredentialProcessHandler implements AwsConfigCredentialSourceHandler {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(CredentialProcessHandler.class);
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private static final long TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_BYTES = 64000;

    public CredentialProcessHandler() {}

    @Override
    public IdentityResult<AwsCredentialsIdentity> tryResolve(
            AwsConfigCredentialSource source,
            ResolutionContext context
    ) {
        if (!(source instanceof AwsConfigCredentialSource.CredentialProcess(String commandLine))) {
            return null;
        }

        try {
            return execute(commandLine);
        } catch (IOException | InterruptedException e) {
            return IdentityResult.ofError(getClass(), "credential_process failed: " + e.getMessage());
        }
    }

    private IdentityResult<AwsCredentialsIdentity> execute(String commandLine)
            throws IOException, InterruptedException {
        List<String> cmd = buildCommand(commandLine);
        Process process = new ProcessBuilder(cmd).redirectErrorStream(false).start();

        String stdout;
        String stderr;
        // Use a shared buffer for stdin/stdout
        byte[] buf = new byte[MAX_OUTPUT_BYTES + 1];
        try (var stdoutStream = process.getInputStream(); var stderrStream = process.getErrorStream()) {
            stdout = readLimited(stdoutStream, buf);
            stderr = readLimited(stderrStream, buf);
        } finally {
            process.destroy();
        }

        // Uses a very generous timeout of 60s.
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return IdentityResult.ofError(getClass(), "credential_process timed out after " + TIMEOUT_SECONDS + "s");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            // Per the SEP: stderr is accessible to the customer but must not be logged at levels above trace.
            LOGGER.debug("credential_process exited with code {}", exitCode);
            String msg = stderr.isBlank() ? "credential_process exited with code " + exitCode : stderr.strip();
            return IdentityResult.ofError(getClass(), msg);
        }

        return parseOutput(stdout);
    }

    // Choose the right shell for windows/not-windows.
    private static List<String> buildCommand(String commandLine) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return List.of("cmd.exe", "/C", commandLine);
        }
        return List.of("sh", "-c", commandLine);
    }

    // Limit response size: read up to 65 bytes, but allow only 64; if 65 bytes was read, it's too much content.
    private static String readLimited(InputStream in, byte[] buf) throws IOException {
        int n = in.readNBytes(buf, 0, buf.length);
        if (n == buf.length) {
            throw new IOException("credential_process output exceeded " + MAX_OUTPUT_BYTES + " bytes");
        }
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    private IdentityResult<AwsCredentialsIdentity> parseOutput(String json) {
        Document doc = CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();

        Document versionNode = doc.getMember("Version");
        if (versionNode != null && versionNode.asInteger() != 1) {
            return IdentityResult.ofError(getClass(),
                    "credential_process output has unsupported Version: " + versionNode.asInteger());
        }

        String accessKeyId = stringMember(doc, "AccessKeyId");
        String secretAccessKey = stringMember(doc, "SecretAccessKey");
        if (accessKeyId == null || secretAccessKey == null) {
            return IdentityResult.ofError(getClass(),
                    "credential_process output missing required AccessKeyId or SecretAccessKey");
        }

        String sessionToken = stringMember(doc, "SessionToken");
        String accountId = stringMember(doc, "AccountId");
        String expirationStr = stringMember(doc, "Expiration");
        Instant expiration = null;
        if (expirationStr != null && !expirationStr.isEmpty()) {
            try {
                expiration = Instant.parse(expirationStr);
            } catch (DateTimeParseException e) {
                LOGGER.warn("credential_process returned unparseable Expiration: {}", expirationStr);
            }
        }

        return IdentityResult.of(AwsCredentialsIdentity.create(
                accessKeyId,
                secretAccessKey,
                sessionToken,
                expiration,
                accountId));
    }

    private static String stringMember(Document doc, String name) {
        Document member = doc.getMember(name);
        return member == null ? null : member.asString();
    }
}
