/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;

/**
 * Converts protocol-test fixture bodies to wire bytes for benchmarks.
 *
 * <p>JSON normalization preserves number lexemes, string escapes, and member order.
 * Bodies must be valid for their media type.
 */
public final class WireBodyNormalizer {

    private WireBodyNormalizer() {}

    /**
     * Returns the wire bytes of a fixture body.
     *
     * <p>Decodes base64 blob {@code @httpPayload} bodies. Preserves string and enum payloads as UTF-8 bytes.
     *
     * <p>For other payloads, resolves the media type from {@code bodyMediaType}, then {@code Content-Type},
     * then {@code defaultContentType}. Decodes base64 CBOR, removes JSON whitespace outside strings,
     * and preserves other bodies as UTF-8 bytes.
     *
     * @param testCase fixture whose body is converted
     * @param messageSchema message schema that defines payload bindings
     * @param defaultContentType the protocol's content type, used when the case declares none
     * @param emptyBody bytes to use when the case has no body
     * @return normalized bytes, or {@code emptyBody} when the fixture has no body
     */
    public static byte[] wireBody(
            HttpMessageTestCase testCase,
            Schema messageSchema,
            String defaultContentType,
            byte[] emptyBody
    ) {
        Objects.requireNonNull(defaultContentType, "defaultContentType");
        var fixture = testCase.getBody();
        if (fixture.isEmpty()) {
            return emptyBody;
        }
        String text = fixture.get();
        Schema payload = httpPayloadMember(messageSchema);
        if (payload != null) {
            if (payload.type() == ShapeType.BLOB) {
                return Base64.getMimeDecoder().decode(text);
            }
            if (payload.type() == ShapeType.STRING || payload.type() == ShapeType.ENUM) {
                return text.getBytes(StandardCharsets.UTF_8);
            }
        }
        String contentType = contentType(testCase, defaultContentType);
        if (isCbor(contentType)) {
            return Base64.getMimeDecoder().decode(text);
        }
        return normalize(text.getBytes(StandardCharsets.UTF_8), contentType);
    }

    static byte[] normalize(byte[] body, String contentType) {
        return isJson(contentType) ? minifyJson(body) : body;
    }

    static String contentType(HttpMessageTestCase testCase, String defaultContentType) {
        return testCase.getBodyMediaType()
                .orElseGet(() -> testCase.getHeaders()
                        .entrySet()
                        .stream()
                        .filter(header -> header.getKey().equalsIgnoreCase("Content-Type"))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(defaultContentType));
    }

    // Preserve whitespace in newline-delimited JSON because it separates records.
    static boolean isJson(String contentType) {
        String subtype = subtype(contentType);
        return subtype.equals("json") || subtype.endsWith("+json") || subtype.startsWith("x-amz-json-");
    }

    static boolean isCbor(String contentType) {
        String subtype = subtype(contentType);
        return subtype.equals("cbor") || subtype.endsWith("+cbor") || subtype.startsWith("x-amz-cbor-");
    }

    private static String subtype(String contentType) {
        int params = contentType.indexOf(';');
        String type = (params < 0 ? contentType : contentType.substring(0, params)).strip().toLowerCase(Locale.ROOT);
        int slash = type.indexOf('/');
        return slash < 0 ? "" : type.substring(slash + 1);
    }

    private static Schema httpPayloadMember(Schema messageSchema) {
        for (Schema member : messageSchema.members()) {
            if (member.hasTrait(TraitKey.HTTP_PAYLOAD_TRAIT)) {
                return member;
            }
        }
        return null;
    }

    private static byte[] minifyJson(byte[] body) {
        byte[] out = new byte[body.length];
        int n = 0;
        boolean inString = false;
        for (int i = 0; i < body.length; i++) {
            byte b = body[i];
            if (inString) {
                out[n++] = b;
                if (b == '\\' && i + 1 < body.length) {
                    out[n++] = body[++i];
                } else if (b == '"') {
                    inString = false;
                }
            } else if (b == '"') {
                inString = true;
                out[n++] = b;
            } else if (!isWhitespace(b)) {
                out[n++] = b;
            }
        }
        return n == body.length ? body : Arrays.copyOf(out, n);
    }

    private static boolean isWhitespace(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t';
    }
}
