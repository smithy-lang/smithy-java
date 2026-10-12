/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.nio.charset.StandardCharsets;
import software.amazon.smithy.java.http.api.HeaderUtils;

class SigningPrimitivesFuzzTest {

    private static final SigningResources RESOURCES = new SigningResources();
    private static final SigningResources MAC_FALLBACK = macFallback();

    @FuzzTest(maxDuration = "5m")
    void headerValueLatin1(byte[] bytes) {
        SigningResourcesPropertyTest.assertHeaderValue(RESOURCES, new String(bytes, StandardCharsets.ISO_8859_1));
    }

    @FuzzTest(maxDuration = "5m")
    void headerValueUtf16(FuzzedDataProvider data) {
        String value = data.consumeString(64);
        if (hasUnpairedSurrogate(value)) {
            // The fallback encoder differs from UTF-8 for unpaired surrogates, which HttpHeaders rejects.
            return;
        }
        SigningResourcesPropertyTest.assertHeaderValue(RESOURCES, value);
    }

    @FuzzTest(maxDuration = "5m")
    void headerNameLowercasing(byte[] bytes) {
        SigningResourcesPropertyTest.assertHeaderName(RESOURCES, new String(bytes, StandardCharsets.ISO_8859_1));
    }

    @FuzzTest(maxDuration = "5m")
    void hmac(FuzzedDataProvider data) throws Exception {
        byte[] key = data.consumeBytes(data.consumeInt(1, 200));
        if (key.length == 0) {
            return; // SecretKeySpec rejects empty keys.
        }
        int pad = data.consumeInt(0, 7);
        boolean fallback = data.consumeBoolean();
        byte[] body = data.consumeRemainingAsBytes();
        SigningResourcesPropertyTest.assertHmac(fallback ? MAC_FALLBACK : RESOURCES, key, body, pad);
    }

    @FuzzTest(maxDuration = "5m")
    void hash(FuzzedDataProvider data) throws Exception {
        int pad = data.consumeInt(0, 9);
        int kind = data.consumeInt(0, 3);
        byte[] body = data.consumeRemainingAsBytes();
        SigningResourcesPropertyTest.assertHash(RESOURCES, body, pad, kind);
    }

    @FuzzTest(maxDuration = "5m")
    void timestamp(long second) {
        if (second < SigningResourcesPropertyTest.MIN_SECOND || second > SigningResourcesPropertyTest.MAX_SECOND) {
            return;
        }
        SigningResourcesPropertyTest.assertTimestamp(RESOURCES, second);
    }

    @FuzzTest(maxDuration = "5m")
    void normalizeHeaderValue(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.ISO_8859_1);
        String expected;
        try {
            expected = normalizeValueReference(value);
        } catch (IllegalArgumentException e) {
            expected = null;
        }
        String got;
        try {
            got = HeaderUtils.normalizeValue(value);
        } catch (IllegalArgumentException e) {
            got = null;
        }
        assertThat(got).isEqualTo(expected);
    }

    private static String normalizeValueReference(String value) {
        int length = value.length();
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c > 0xFF || (c < 0x20 && c != '\t') || c == 0x7F) {
                throw new IllegalArgumentException("Invalid header value: " + value);
            }
        }
        int start = 0;
        while (start < length && (value.charAt(start) == ' ' || value.charAt(start) == '\t')) {
            start++;
        }
        int end = length;
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\t')) {
            end--;
        }
        return start == 0 && end == length ? value : value.substring(start, end);
    }

    private static boolean hasUnpairedSurrogate(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    i++;
                } else {
                    return true;
                }
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    private static SigningResources macFallback() {
        try {
            return SigningResourcesPropertyTest.withoutDigestCloning(new SigningResources());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
