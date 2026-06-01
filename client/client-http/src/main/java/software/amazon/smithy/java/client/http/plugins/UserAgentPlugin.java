/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.util.Locale;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientContext;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.Version;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * A plugin used to apply a default User-Agent.
 *
 * <p>This plugin is applied by default by {@link HttpMessageExchange} and {@link ClientTransport}s that call it by
 * default.
 */
@SmithyInternalApi
public final class UserAgentPlugin implements AutoClientPlugin {
    @Override
    public void configureClient(ClientConfig.Builder config) {
        // We can conditionally add the interceptor here because client transport can't change after construction.
        if (config.isUsingMessageExchange(HttpMessageExchange.INSTANCE)) {
            config.addInterceptor(UserAgentInterceptor.INSTANCE);
        }
    }

    /**
     * Adds a default User-Agent header if none is set.
     *
     * <p>The agent is in the form of {@code smithy-java/0.1 ua/2.1 os/macos#14.6.1Lang/java#21.0.12 m/a,b}, where
     * "m/a,b" are feature IDs set via {@link CallContext#FEATURE_IDS}.
     *
     * <p>A pair of "app/{id}" is added if {@link ClientContext#APPLICATION_ID} is set, or a value is set in the
     * "aws.userAgentAppId" system property, or the value set in the "AWS_SDK_UA_APP_ID" environment variable.
     * See <a href="https://docs.aws.amazon.com/sdkref/latest/guide/feature-appid.html">Application ID</a> for more
     * information.
     */
    static final class UserAgentInterceptor implements ClientInterceptor {

        private static final UserAgentInterceptor INSTANCE = new UserAgentInterceptor();

        private static final String UA_VERSION = "2.1";
        private static final String STATIC_SEGMENT;
        // The User-Agent for the common request: no app-id and no per-call feature IDs. Equal to
        // STATIC_SEGMENT with its single trailing space trimmed. Precomputed so those requests
        // skip the StringBuilder + toString churn entirely (createUa profiled as a per-request
        // byte[]/String allocator), reserving the builder path for requests that actually carry an
        // app-id or feature IDs.
        private static final String DEFAULT_UA;

        private static final String ENV_APP_ID = "AWS_SDK_UA_APP_ID";
        private static final String SYSTEM_APP_ID = "aws.userAgentAppId";
        private static final String ENV_APPLICATION_ID = System.getenv(ENV_APP_ID);

        private static final String UA_DENYLIST_CHARS = "[() ,/:;<=>?@[]{}\\]";
        private static final char REPLACEMENT = '_';
        private static final int ASCII_LIMIT = 128; // For ASCII characters
        private static final boolean[] DENYLIST = new boolean[ASCII_LIMIT];

        static {
            for (char c : UA_DENYLIST_CHARS.toCharArray()) {
                DENYLIST[c] = true;
            }

            STATIC_SEGMENT = "smithy-java/" + Version.VERSION
                    + " ua/" + UA_VERSION
                    + " os/" + getOsFamily() + "#" + sanitizeValue(System.getProperty("os.version"))
                    + " lang/java#" + sanitizeValue(System.getProperty("java.version"))
                    + ' ';
            DEFAULT_UA = STATIC_SEGMENT.substring(0, STATIC_SEGMENT.length() - 1);
        }

        @Override
        public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
            // Run after auth resolution + signing so business-metric IDs from identity resolvers
            // (CredentialChain providers, S3ExpressIdentityProvider, etc.) are reflected in the
            // "m/..." segment. user-agent is not part of SigV4's signed-headers list, so stamping
            // it after signing is safe.
            if (hook.request() instanceof HttpRequest req && !req.headers().hasHeader(HeaderName.USER_AGENT)) {
                var updated = req.toModifiable();
                updated.headers().setHeader(HeaderName.USER_AGENT, createUa(hook.context()));
                return hook.asRequestType(updated);
            }
            return hook.request();
        }

        private static String createUa(Context context) {
            var appId = resolveAppId(context);
            var features = context.get(CallContext.FEATURE_IDS);
            // Common case — neither app-id nor feature IDs — is a constant; skip the builder.
            if (appId == null && (features == null || features.isEmpty())) {
                return DEFAULT_UA;
            }

            StringBuilder b = new StringBuilder(STATIC_SEGMENT);

            if (appId != null) {
                b.append("app/").append(sanitizeValue(appId)).append(' ');
            }

            if (features != null && !features.isEmpty()) {
                b.append("m/");
                for (var feature : features) {
                    b.append(sanitizeValue(feature.getShortName())).append(',');
                }
                b.deleteCharAt(b.length() - 1);
            } else {
                // Trim the last trailing character.
                b.deleteCharAt(STATIC_SEGMENT.length() - 1);
            }

            return b.toString();
        }

        private static String resolveAppId(Context context) {
            var appId = context.get(ClientContext.APPLICATION_ID);
            if (appId == null) {
                appId = System.getenv(SYSTEM_APP_ID);
            }
            if (appId == null) {
                appId = ENV_APPLICATION_ID;
            }
            return appId;
        }

        private static String getOsFamily() {
            String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            if (osName.contains("win")) {
                return "windows";
            } else if (osName.contains("mac")) {
                return "macos";
            } else if (osName.contains("nux") || osName.contains("nix") || osName.contains("aix")) {
                return "linux";
            } else if (osName.contains("android")) {
                return "android";
            } else if (osName.contains("ios")) {
                return "ios";
            } else if (osName.contains("watchos")) {
                return "watchos";
            } else if (osName.contains("tvos")) {
                return "tvos";
            } else if (osName.contains("visionos")) {
                return "visionos";
            } else {
                return "other";
            }
        }

        private static String sanitizeValue(String input) {
            if (input == null) {
                return "unknown";
            }

            for (var i = 0; i < input.length(); i++) {
                var c = input.charAt(i);
                if (c < ASCII_LIMIT && DENYLIST[c]) {
                    return sanitizeValueSlow(input);
                }
            }
            return input;
        }

        private static String sanitizeValueSlow(String input) {
            StringBuilder result = new StringBuilder(input.length());
            for (char c : input.toCharArray()) {
                if (c < ASCII_LIMIT && DENYLIST[c]) {
                    result.append(REPLACEMENT);
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
    }
}
