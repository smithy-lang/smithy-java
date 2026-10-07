/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

class WireBodyMediaTypeTest {
    @Test
    void preservesWrappedBlobPayloadWithJsonProtocolDefault() {
        var testCase = testCaseBuilder()
                .putHeader("Content-Type", "application/octet-stream")
                .build();
        byte[] body = bytes(" YmxvYi1h\r\nbHBoYQ== \t");
        assertArrayEquals(body, WireBodyNormalizer.normalize(body, testCase, "application/json"));
    }

    @Test
    void preservesStringPayloadWithJsonProtocolDefault() {
        var testCase = testCaseBuilder()
                .putHeader("cOnTeNt-TyPe", "text/plain; charset=utf-8")
                .build();
        byte[] body = bytes(" hello \tworld\r\n ");
        assertArrayEquals(body, WireBodyNormalizer.normalize(body, testCase, "application/json"));
    }

    @Test
    void bodyMediaTypeOverridesHeaderAndProtocolDefault() {
        var testCase = testCaseBuilder()
                .bodyMediaType("text/plain")
                .putHeader("Content-Type", "application/json")
                .build();
        byte[] body = bytes(" hello world ");
        assertArrayEquals(body, WireBodyNormalizer.normalize(body, testCase, "application/json"));
    }

    @Test
    void minifiesJsonDeclaredByBodyMediaType() {
        var testCase = testCaseBuilder()
                .bodyMediaType("application/json")
                .putHeader("Content-Type", "application/octet-stream")
                .build();
        assertArrayEquals(bytes("{\"s\":\" a b \"}"),
                WireBodyNormalizer.normalize(bytes(" { \"s\": \" a b \" } "), testCase, "application/xml"));
    }

    @Test
    void minifiesJsonDeclaredByHeader() {
        var testCase = testCaseBuilder()
                .putHeader("content-type", "Application/X-Amz-Json-1.0; charset=utf-8")
                .build();
        assertArrayEquals(bytes("{\"n\":1}"),
                WireBodyNormalizer.normalize(bytes(" { \"n\": 1 } "), testCase, "application/xml"));
    }

    @Test
    void usesProtocolDefaultWhenTestCaseHasNoMediaType() {
        var testCase = testCaseBuilder().build();
        byte[] body = bytes(" { \"n\": 1 } ");
        assertArrayEquals(bytes("{\"n\":1}"), WireBodyNormalizer.normalize(body, testCase, "application/json"));
        assertArrayEquals(body, WireBodyNormalizer.normalize(body, testCase, "application/xml"));
    }

    private static HttpResponseTestCase.Builder testCaseBuilder() {
        return HttpResponseTestCase.builder()
                .id("payload")
                .protocol(ShapeId.from("aws.protocols#restJson1"))
                .code(200);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
