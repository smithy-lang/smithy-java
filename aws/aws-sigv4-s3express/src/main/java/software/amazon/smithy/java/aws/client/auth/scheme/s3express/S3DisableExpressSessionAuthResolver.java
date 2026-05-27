/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Plugin that resolves the {@code DisableS3ExpressSessionAuth} setting from environment and
 * shared-config sources, and stamps it on the client context for the rules engine to consume.
 *
 * <p>Per the S3 Express SEP, precedence (highest first):
 *
 * <ol>
 *     <li>Explicit context setting —
 *         {@code .putConfig(S3EndpointSettings.S3_DISABLE_EXPRESS_SESSION_AUTH, true)} on the
 *         client builder. Honored as-is; this plugin does not overwrite it.</li>
 *     <li>{@code AWS_S3_DISABLE_EXPRESS_SESSION_AUTH} environment variable.</li>
 *     <li>{@code s3_disable_express_session_auth} property in the shared config file under the
 *         active profile.</li>
 *     <li>Default: {@code false} (session auth enabled).</li>
 * </ol>
 *
 * <p>Only {@code true} or {@code false} are accepted; anything else throws.
 */
@SmithyUnstableApi
public final class S3DisableExpressSessionAuthResolver implements ClientPlugin {

    private static final String ENV_VAR = "AWS_S3_DISABLE_EXPRESS_SESSION_AUTH";
    private static final String PROFILE_PROPERTY = "s3_disable_express_session_auth";

    public static final S3DisableExpressSessionAuthResolver INSTANCE = new S3DisableExpressSessionAuthResolver();

    private S3DisableExpressSessionAuthResolver() {}

    @Override
    public Phase getPluginPhase() {
        return Phase.DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // Explicit setting via the client builder wins; don't overwrite.
        if (config.context().get(S3EndpointSettings.S3_DISABLE_EXPRESS_SESSION_AUTH) != null) {
            return;
        }

        String envValue = System.getenv(ENV_VAR);
        if (envValue != null) {
            config.putConfig(S3EndpointSettings.S3_DISABLE_EXPRESS_SESSION_AUTH, parseBoolean(envValue, ENV_VAR));
            return;
        }

        // Profile lookup is best-effort: a missing config file or unreadable profile is not an
        // error, just falls through to the default.
        try {
            var profileFile = AwsProfileFile.loadSilently();
            var profile = profileFile.profile(profileName());
            if (profile != null) {
                String profileValue = profile.property(PROFILE_PROPERTY);
                if (profileValue != null) {
                    config.putConfig(
                            S3EndpointSettings.S3_DISABLE_EXPRESS_SESSION_AUTH,
                            parseBoolean(profileValue, PROFILE_PROPERTY));
                }
            }
        } catch (RuntimeException ignored) {
            // No profile file, unreadable, or invalid — fall through to the rules engine default.
        }
    }

    private static String profileName() {
        String fromSystemProp = System.getProperty("aws.profile");
        if (fromSystemProp != null && !fromSystemProp.isEmpty()) {
            return fromSystemProp;
        }
        String fromEnv = System.getenv("AWS_PROFILE");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            return fromEnv;
        }
        return "default";
    }

    private static boolean parseBoolean(String value, String source) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(
                "Invalid value for " + source + ": '" + value + "'. Must be 'true' or 'false'.");
    }
}
