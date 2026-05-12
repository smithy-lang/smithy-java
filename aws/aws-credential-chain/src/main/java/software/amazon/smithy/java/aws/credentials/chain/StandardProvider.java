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
    /** Credentials explicitly provided in code. */
    CODE(null) {
        @Override
        public boolean isDetected() {
            return false;
        }
    },

    /** Credentials from JVM system properties ({@code aws.accessKeyId}, etc.). */
    JAVA_SYSTEM_PROPERTIES("software.amazon.smithy.java:aws-client-core") {
        @Override
        public boolean isDetected() {
            return System.getProperty("aws.accessKeyId") != null;
        }
    },

    /** Credentials from environment variables ({@code AWS_ACCESS_KEY_ID}, etc.). */
    ENVIRONMENT("software.amazon.smithy.java:aws-client-core") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_ACCESS_KEY_ID") != null;
        }
    },

    /** Web identity token from environment variables ({@code AWS_WEB_IDENTITY_TOKEN_FILE} + {@code AWS_ROLE_ARN}). */
    WEB_IDENTITY_TOKEN_ENV("software.amazon.smithy.java:aws-credentials-sts") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE") != null && System.getenv("AWS_ROLE_ARN") != null;
        }
    },

    /** Credentials from the AWS shared config/credentials files. */
    SHARED_CONFIG("software.amazon.smithy.java:aws-config") {
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

    /** Credentials from an HTTP endpoint (ECS container, EKS pod identity, etc.). */
    ECS_CONTAINER("software.amazon.smithy.java:aws-credentials-ecs") {
        @Override
        public boolean isDetected() {
            return System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null
                    || System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null;
        }
    },

    /** Credentials from EC2 instance metadata service (IMDS). */
    EC2_INSTANCE_METADATA("software.amazon.smithy.java:aws-credentials-imds") {
        @Override
        public boolean isDetected() {
            // No cheap signal; IMDS requires a network call to detect.
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
