/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.plugins;

import java.util.List;
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
 *
 * <p>Clients resolve a default {@link ClientContext#APPLICATION_ID} once per build. They use the first non-blank value
 * from the {@code aws.userAgentAppId} and {@code sdk.ua.appId} system properties, in that order. The
 * {@code AWS_SDK_UA_APP_ID} environment variable is the final source. Builder settings and per-request overrides take
 * precedence. See <a href="https://docs.aws.amazon.com/sdkref/latest/guide/feature-appid.html">Application ID</a>.
 */
@SmithyInternalApi
public final class UserAgentPlugin implements AutoClientPlugin {

    private static final List<String> APP_ID_SYSTEM_PROPERTIES = List.of("aws.userAgentAppId", "sdk.ua.appId");
    private static final String APP_ID_ENV_VAR = "AWS_SDK_UA_APP_ID";

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // We can conditionally add the interceptor here because client transport can't change after construction.
        if (config.isUsingMessageExchange(HttpMessageExchange.INSTANCE)) {
            var appId = resolveDefaultAppId();
            if (appId != null) {
                config.putConfigIfAbsent(ClientContext.APPLICATION_ID, appId);
            }
            config.addInterceptor(UserAgentInterceptor.INSTANCE);
        }
    }

    static String resolveDefaultAppId() {
        for (var property : APP_ID_SYSTEM_PROPERTIES) {
            var value = normalizeAppId(System.getProperty(property));
            if (value != null) {
                return value;
            }
        }
        return normalizeAppId(System.getenv(APP_ID_ENV_VAR));
    }

    private static String normalizeAppId(String value) {
        if (value == null) {
            return null;
        }
        var stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

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
            var appId = context.get(ClientContext.APPLICATION_ID);
            if (appId != null && appId.isEmpty()) {
                appId = null;
            }
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
            }

            // Every segment must end with a space or comma.
            b.setLength(b.length() - 1);
            return b.toString();
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
