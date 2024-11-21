/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.useragent;

import java.util.List;
import java.util.Locale;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Adds a default User-Agent header if none is set.
 *
 * <p>The agent is in the form of {@code smithy-java/0.1 ua/2.1 os/macos#14.6.1Lang/java#17.0.12 m/a,b}, where
 * "m/a,b" are feature IDs set via {@link CallContext#FEATURE_IDS}.
 */
public final class UserAgentInterceptor implements ClientInterceptor {

    public static final UserAgentInterceptor INSTANCE = new UserAgentInterceptor();

    private static final String UA_VERSION = "2.1";
    // TODO: add a constant to core that gets updated on each release.
    private static final String SMITHY_VERSION = "0.1";
    private static final String STATIC_SEGMENT;

    private static final String UA_DENYLIST_CHARS = "[() ,/:;<=>?@[]{}\\]";
    private static final char REPLACEMENT = '_';
    private static final int ASCII_LIMIT = 128; // For ASCII characters
    private static final boolean[] DENYLIST = new boolean[ASCII_LIMIT];

    static {
        for (char c : UA_DENYLIST_CHARS.toCharArray()) {
            DENYLIST[c] = true;
        }

        STATIC_SEGMENT = "smithy-java/" + SMITHY_VERSION
            + " ua/" + UA_VERSION
            + " os/" + getOsFamily() + "#" + sanitizeValue(System.getProperty("os.version"))
            + " lang/java#" + sanitizeValue(System.getProperty("java.version"))
            + ' ';
    }

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        return hook.mapRequest(HttpRequest.class, h -> {
            if (!h.request().headers().hasHeader("user-agent")) {
                return h.request()
                    .toBuilder()
                    .withReplacedHeader("user-agent", List.of(createUa(h.context())))
                    .build();
            }
            return h.request();
        });
    }

    private static String createUa(Context context) {
        StringBuilder b = new StringBuilder(STATIC_SEGMENT);

        var appId = context.get(CallContext.APPLICATION_ID);
        if (appId != null) {
            b.append("app/").append(sanitizeValue(appId)).append(' ');
        }

        var features = context.get(CallContext.FEATURE_IDS);
        if (features != null && !features.isEmpty()) {
            b.append("m/");
            for (var feature : features) {
                b.append(sanitizeValue(feature)).append(',');
            }
            b.deleteCharAt(b.length() - 1);
        } else {
            // Trim the last trailing character.
            b.deleteCharAt(STATIC_SEGMENT.length() - 1);
        }

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
