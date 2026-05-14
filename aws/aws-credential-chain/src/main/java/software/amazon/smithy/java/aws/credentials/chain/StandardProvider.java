/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standard credential provider slots in the AWS default credential chain.
 *
 * <p>These are ordered from highest to lowest priority. If no implementation is registered for a slot, that slot is
 * skipped in the chain.
 *
 * <p>Each slot knows how to cheaply detect whether credentials of that type are likely available
 * (via {@link #isDetected()}), and what dependency to suggest if the implementation is missing
 * (via {@link #moduleSuggestion()}).
 */
public enum StandardProvider {
    /**
     * Credentials from JVM system properties.
     *
     * <p>Detected when {@code aws.accessKeyId} is set. Skipped on platforms without
     * language-level property systems.
     */
    JAVA_SYSTEM_PROPERTIES("software.amazon.smithy.java:aws-client-core") {
        @Override
        public boolean isDetected() {
            return System.getProperty("aws.accessKeyId") != null;
        }
    },

    /**
     * Credentials from environment variables ({@code AWS_ACCESS_KEY_ID},
     * {@code AWS_SECRET_ACCESS_KEY}).
     */
    ENVIRONMENT("software.amazon.smithy.java:aws-client-core") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_ACCESS_KEY_ID") != null;
        }
    },

    /**
     * Web identity token from environment variables.
     *
     * <p>Detected when both {@code AWS_WEB_IDENTITY_TOKEN_FILE} and {@code AWS_ROLE_ARN}
     * are set. Requires an STS module to resolve.
     */
    WEB_IDENTITY_TOKEN_ENV("software.amazon.smithy.java:aws-credentials-sts") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") != null && System.getenv("AWS_ROLE_ARN") != null;
        }
    },

    /**
     * Parses AWS shared config/credentials files and stores the result on the
     * {@link ProviderContext} for downstream providers.
     *
     * <p>This provider does not itself resolve credentials — it returns {@code null} from
     * {@code create()}. Its purpose is to make the parsed profile available via
     * {@link ProviderContext#profile()} for all subsequent profile-based slots.
     */
    SHARED_CONFIG(null) {
        @Override
        public boolean isDetected() {
            var home = System.getProperty("user.home");
            if (home == null) {
                return false;
            }
            var awsDir = Path.of(home, ".aws");
            return Files.exists(awsDir.resolve("credentials")) || Files.exists(awsDir.resolve("config"));
        }
    },

    /**
     * Profile-based web identity token ({@code web_identity_token_file} + {@code role_arn}).
     *
     * <p>Requires the STS module. Reads the active profile from {@link ProviderContext#profile()}.
     */
    /**
     * Profile-based static keys ({@code aws_access_key_id} + {@code aws_secret_access_key}).
     *
     * <p>Re-reads from the profile on each resolution to support live reload after invalidation.
     */
    PROFILE_STATIC_KEYS(null) {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based session keys ({@code aws_access_key_id} + {@code aws_secret_access_key}
     * + {@code aws_session_token}).
     *
     * <p>Re-reads from the profile on each resolution to support live reload after invalidation.
     */
    PROFILE_SESSION_KEYS(null) {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based assume role ({@code role_arn} with {@code source_profile} or
     * {@code credential_source}).
     *
     * <p>Requires the STS module. Reads the active profile from {@link ChainSetup#profile()}.
     */
    PROFILE_ASSUME_ROLE("software.amazon.smithy.java:aws-credentials-sts") {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based web identity token ({@code web_identity_token_file} + {@code role_arn}).
     *
     * <p>Requires the STS module. Reads the active profile from {@link ChainSetup#profile()}.
     */
    PROFILE_WEB_IDENTITY("software.amazon.smithy.java:aws-credentials-sts") {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based SSO session ({@code sso_session} + {@code sso_account_id} +
     * {@code sso_role_name}).
     *
     * <p>Requires the SSO module. Reads the active profile from {@link ChainSetup#profile()}.
     */
    PROFILE_SSO_SESSION("software.amazon.smithy.java:aws-credentials-sso") {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based legacy SSO ({@code sso_start_url} + {@code sso_account_id} +
     * {@code sso_role_name} + {@code sso_region}).
     *
     * <p>Requires the SSO module. Reads the active profile from {@link ChainSetup#profile()}.
     */
    PROFILE_LEGACY_SSO("software.amazon.smithy.java:aws-credentials-sso") {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based login session ({@code login_session}).
     *
     * <p>Requires the login module. Reads the active profile from {@link ChainSetup#profile()}.
     */
    PROFILE_LOGIN("software.amazon.smithy.java:aws-credentials-login") {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Profile-based credential process ({@code credential_process}).
     *
     * <p>Invokes an external process on each resolution. The command string is captured at
     * assembly time from the active profile.
     */
    PROFILE_CREDENTIAL_PROCESS(null) {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /**
     * Credentials from an HTTP endpoint (ECS container credentials, EKS pod identity).
     *
     * <p>Detected when {@code AWS_CONTAINER_CREDENTIALS_FULL_URI} or
     * {@code AWS_CONTAINER_CREDENTIALS_RELATIVE_URI} is set.
     */
    ECS_CONTAINER("software.amazon.smithy.java:aws-credentials-ecs") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null
                    || System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null;
        }
    },

    /**
     * Credentials from the EC2 Instance Metadata Service (IMDSv2).
     *
     * <p>No cheap detection signal — IMDS requires a network call. Always tried last.
     */
    EC2_INSTANCE_METADATA("software.amazon.smithy.java:aws-credentials-imds") {
        @Override
        public boolean isDetected() {
            return false;
        }
    };

    private final String moduleSuggestion;

    StandardProvider(String moduleSuggestion) {
        this.moduleSuggestion = moduleSuggestion;
    }

    /**
     * Cheaply detect whether this credential source is likely available in the current environment.
     * This must not perform network calls or expensive I/O.
     *
     * @return {@code true} if signals suggest this source is configured.
     */
    public abstract boolean isDetected();

    /**
     * @return the Maven coordinate to suggest when this source is detected but no implementation
     *         is on the classpath, or {@code null} if no suggestion is available.
     */
    public String moduleSuggestion() {
        return moduleSuggestion;
    }
}
