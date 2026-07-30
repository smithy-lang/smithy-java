/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;

/**
 * Resolver that calls STS AssumeRoleWithWebIdentity using a token file and role ARN.
 *
 * <p>This operation does not require source credentials (the web identity token is the
 * authentication), so the STS client is configured with no auth.
 */
final class StsWebIdentityResolver implements IdentityResolver<AwsCredentialsIdentity>, AutoCloseable {

    private static final Set<String> EXPIRED_TOKEN_ERRORS = Set.of("ExpiredToken", "ExpiredTokenException");

    private final AwsConfigCredentialSource.WebIdentityToken source;
    private final WebIdentityCall call;
    private final AutoCloseable closeable;

    StsWebIdentityResolver(AwsConfigCredentialSource.WebIdentityToken source, DynamicClient client) {
        this(source, input -> client.call("AssumeRoleWithWebIdentity", input), client);
    }

    StsWebIdentityResolver(AwsConfigCredentialSource.WebIdentityToken source, WebIdentityCall call) {
        this(source, call, () -> {});
    }

    private StsWebIdentityResolver(
            AwsConfigCredentialSource.WebIdentityToken source,
            WebIdentityCall call,
            AutoCloseable closeable
    ) {
        this.source = source;
        this.call = call;
        this.closeable = closeable;
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
        try {
            try {
                return callWithCurrentToken();
            } catch (RuntimeException error) {
                if (!isExpiredToken(error)) {
                    throw error;
                }
                return callWithCurrentToken();
            }
        } catch (RuntimeException | IOException e) {
            var msg = String.format("Failed to assume role with web identity (role=%s): %s",
                    source.roleArn(),
                    e.getMessage());
            throw new RuntimeException(msg, e);
        }
    }

    private IdentityResult<AwsCredentialsIdentity> callWithCurrentToken() throws IOException {
        var token = Files.readString(Path.of(source.webIdentityTokenFile()), StandardCharsets.UTF_8).trim();
        var sessionName = source.roleSessionName() != null
                ? source.roleSessionName()
                : "smithy-java-" + System.currentTimeMillis();

        return parseCredentials(call.call(Map.of(
                "RoleArn",
                source.roleArn(),
                "RoleSessionName",
                sessionName,
                "WebIdentityToken",
                token)));
    }

    private static boolean isExpiredToken(RuntimeException error) {
        return error instanceof ModeledException modeled
                && EXPIRED_TOKEN_ERRORS.contains(modeled.schema().id().getName());
    }

    @Override
    public void close() {
        try {
            closeable.close();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to close STS client", error);
        }
    }

    @FunctionalInterface
    interface WebIdentityCall {
        Document call(Map<String, Object> input);
    }

    static IdentityResult<AwsCredentialsIdentity> parseCredentials(Document output) {
        Document creds = output.getMember("Credentials");
        String accessKeyId = creds.getMember("AccessKeyId").asString();
        String secretAccessKey = creds.getMember("SecretAccessKey").asString();
        String sessionToken = creds.getMember("SessionToken").asString();
        Instant expiration = Instant.parse(creds.getMember("Expiration").asString());

        String accountId = null;
        Document assumedRoleUser = output.getMember("AssumedRoleUser");
        if (assumedRoleUser != null) {
            accountId = parseAccountId(assumedRoleUser);
        }

        return IdentityResult.of(AwsCredentialsIdentity.create(
                accessKeyId,
                secretAccessKey,
                sessionToken,
                expiration,
                accountId));
    }

    private static String parseAccountId(Document assumedRoleUser) {
        var arn = assumedRoleUser.getMember("Arn").asString();
        if (arn != null && arn.contains(":")) {
            var parts = arn.split(":");
            if (parts.length >= 5) {
                return parts[4];
            }
        }

        return null;
    }
}
