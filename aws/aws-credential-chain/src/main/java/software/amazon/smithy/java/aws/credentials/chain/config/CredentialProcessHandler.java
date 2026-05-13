/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.CreateResult;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Resolves credentials by invoking an external process specified by {@code credential_process}
 * in the active AWS profile.
 *
 * <p>The command string is captured at assembly time and does not change after construction.
 * Each call to {@code resolveIdentity()} re-executes the process to obtain fresh credentials,
 * so expiring credentials returned by the process are naturally refreshed without caching.
 */
public final class CredentialProcessHandler implements ChainIdentityProvider {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(CredentialProcessHandler.class);
    private static final JsonCodec CODEC = JsonCodec.builder().build();
    private static final long TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_BYTES = 64000;
    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(
            new CredentialFeatureId("v"),
            new CredentialFeatureId("w"));
    private static final IdentityResult<AwsCredentialsIdentity> NO_PROFILE =
            IdentityResult.ofError(CredentialProcessHandler.class, "No active profile");
    private static final IdentityResult<AwsCredentialsIdentity> NOT_FOUND =
            IdentityResult.ofError(CredentialProcessHandler.class, "No credential_process in profile");

    @Override
    public String name() {
        return "CredentialProcess";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.PROFILE_CREDENTIAL_PROCESS);
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context) {
        if (identityType != AwsCredentialsIdentity.class) {
            return CreateResult.pass();
        }

        AwsProfile profile = context.profile();
        if (profile == null) {
            return CreateResult.pass();
        }

        for (AwsConfigCredentialSource source : profile.credentialSources()) {
            if (source instanceof AwsConfigCredentialSource.CredentialProcess(String commandLine)) {
                return (CreateResult<I>) new CreateResult.PossibleMatch<>(new Resolver(commandLine));
            }
        }

        return CreateResult.pass();
    }

    private record Resolver(String commandLine) implements IdentityResolver<AwsCredentialsIdentity> {
        @Override
        public Class<AwsCredentialsIdentity> identityType() {
            return AwsCredentialsIdentity.class;
        }

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
            try {
                return execute(commandLine);
            } catch (IOException | InterruptedException e) {
                return IdentityResult.ofError(CredentialProcessHandler.class,
                        "credential_process failed: " + e.getMessage());
            }
        }
    }

    private static IdentityResult<AwsCredentialsIdentity> execute(String commandLine)
            throws IOException, InterruptedException {
        List<String> cmd = buildCommand(commandLine);
        Process process = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        byte[] buf = new byte[MAX_OUTPUT_BYTES + 1];
        String stdout;
        String stderr;
        try (var stdoutStream = process.getInputStream(); var stderrStream = process.getErrorStream()) {
            stdout = readLimited(stdoutStream, buf);
            stderr = readLimited(stderrStream, buf);
        } finally {
            process.destroy();
        }
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return IdentityResult.ofError(CredentialProcessHandler.class,
                    "credential_process timed out after " + TIMEOUT_SECONDS + "s");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            LOGGER.debug("credential_process exited with code {}", exitCode);
            String msg = stderr.isBlank() ? "credential_process exited with code " + exitCode : stderr.strip();
            return IdentityResult.ofError(CredentialProcessHandler.class, msg);
        }
        return parseOutput(stdout);
    }

    private static List<String> buildCommand(String commandLine) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            return List.of("cmd.exe", "/C", commandLine);
        }
        return List.of("sh", "-c", commandLine);
    }

    private static String readLimited(InputStream in, byte[] buf) throws IOException {
        int n = in.readNBytes(buf, 0, buf.length);
        if (n == buf.length) {
            throw new IOException("credential_process output exceeded " + MAX_OUTPUT_BYTES + " bytes");
        }
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    private static IdentityResult<AwsCredentialsIdentity> parseOutput(String json) {
        Document doc = CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
        Document versionNode = doc.getMember("Version");
        if (versionNode != null && versionNode.asInteger() != 1) {
            return IdentityResult.ofError(CredentialProcessHandler.class,
                    "credential_process output has unsupported Version: " + versionNode.asInteger());
        }

        String accessKeyId = stringMember(doc, "AccessKeyId");
        String secretAccessKey = stringMember(doc, "SecretAccessKey");
        if (accessKeyId == null || secretAccessKey == null) {
            return IdentityResult.ofError(CredentialProcessHandler.class,
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
