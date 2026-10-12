/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase;

class WireBodyMediaTypeTest {

    private static final byte[] EMPTY = new byte[0];
    private static final Schema NO_PAYLOAD = Schema.structureBuilder(ShapeId.from("test#Output")).build();
    private static final Schema BLOB_PAYLOAD = Schema.structureBuilder(ShapeId.from("test#BlobOutput"))
            .putMember("Body", PreludeSchemas.BLOB, new HttpPayloadTrait())
            .build();
    private static final Schema STRING_PAYLOAD = Schema.structureBuilder(ShapeId.from("test#StringOutput"))
            .putMember("Body", PreludeSchemas.STRING, new HttpPayloadTrait())
            .build();
    private static final Schema STRUCTURE_PAYLOAD = Schema.structureBuilder(ShapeId.from("test#StructureOutput"))
            .putMember("Nested", NO_PAYLOAD, new HttpPayloadTrait())
            .build();

    @Test
    void preservesOctetStreamBodyWithJsonProtocolDefault() {
        var testCase = testCaseBuilder()
                .putHeader("Content-Type", "application/octet-stream")
                .body(" YmxvYi1h\r\nbHBoYQ== \t")
                .build();
        assertArrayEquals(bytes(" YmxvYi1h\r\nbHBoYQ== \t"), wireBody(testCase, NO_PAYLOAD, "application/json"));
    }

    @Test
    void preservesTextPlainBodyWithJsonProtocolDefault() {
        var testCase = testCaseBuilder()
                .putHeader("cOnTeNt-TyPe", "text/plain; charset=utf-8")
                .body(" hello \tworld\r\n ")
                .build();
        assertArrayEquals(bytes(" hello \tworld\r\n "), wireBody(testCase, NO_PAYLOAD, "application/json"));
    }

    @Test
    void bodyMediaTypeOverridesHeaderAndProtocolDefault() {
        var testCase = testCaseBuilder()
                .bodyMediaType("text/plain")
                .putHeader("Content-Type", "application/json")
                .body(" hello world ")
                .build();
        assertArrayEquals(bytes(" hello world "), wireBody(testCase, NO_PAYLOAD, "application/json"));
    }

    @Test
    void minifiesJsonDeclaredByBodyMediaType() {
        var testCase = testCaseBuilder()
                .bodyMediaType("application/json")
                .putHeader("Content-Type", "application/octet-stream")
                .body(" { \"s\": \" a b \" } ")
                .build();
        assertArrayEquals(bytes("{\"s\":\" a b \"}"), wireBody(testCase, NO_PAYLOAD, "application/xml"));
    }

    @Test
    void minifiesJsonDeclaredByHeader() {
        var testCase = testCaseBuilder()
                .putHeader("content-type", "Application/X-Amz-Json-1.0; charset=utf-8")
                .body(" { \"n\": 1 } ")
                .build();
        assertArrayEquals(bytes("{\"n\":1}"), wireBody(testCase, NO_PAYLOAD, "application/xml"));
    }

    @Test
    void usesProtocolDefaultWhenTestCaseHasNoMediaType() {
        var testCase = testCaseBuilder().body(" { \"n\": 1 } ").build();
        assertArrayEquals(bytes("{\"n\":1}"), wireBody(testCase, NO_PAYLOAD, "application/json"));
        assertArrayEquals(bytes(" { \"n\": 1 } "), wireBody(testCase, NO_PAYLOAD, "application/xml"));
    }

    @Test
    void decodesBlobPayloadRegardlessOfMediaType() {
        var testCase = testCaseBuilder()
                .putHeader("Content-Type", "application/json")
                .body("YmxvYi1h\r\nbHBoYQ==")
                .build();
        assertArrayEquals(bytes("blob-alpha"), wireBody(testCase, BLOB_PAYLOAD, "application/json"));
    }

    @Test
    void preservesStringPayloadWithoutContentTypeUnderJsonProtocolDefault() {
        var testCase = testCaseBuilder().body(" hello \tworld\r\n ").build();
        assertArrayEquals(bytes(" hello \tworld\r\n "), wireBody(testCase, STRING_PAYLOAD, "application/json"));
    }

    @Test
    void minifiesStructurePayloadUnderJsonProtocolDefault() {
        var testCase = testCaseBuilder().body(" { \"n\": 1 } ").build();
        assertArrayEquals(bytes("{\"n\":1}"), wireBody(testCase, STRUCTURE_PAYLOAD, "application/json"));
    }

    @Test
    void decodesCborBodiesFromBase64() {
        var testCase = testCaseBuilder().body("oWFu\r\nAQ==").build();
        byte[] expected = {(byte) 0xa1, 0x61, 0x6e, 0x01};
        assertArrayEquals(expected, wireBody(testCase, NO_PAYLOAD, "application/cbor"));
        assertArrayEquals(expected,
                wireBody(testCaseBuilder().body("oWFuAQ==").putHeader("Content-Type", "application/cbor").build(),
                        NO_PAYLOAD,
                        "application/json"));
    }

    @Test
    void usesEmptyBodyWhenTestCaseHasNone() {
        byte[] empty = bytes("{}");
        assertSame(empty,
                WireBodyNormalizer.wireBody(testCaseBuilder().build(), NO_PAYLOAD, "application/json", empty));
    }

    @Test
    void rejectsNullProtocolContentType() {
        var testCase = testCaseBuilder().body("{}").build();
        assertThrows(NullPointerException.class,
                () -> WireBodyNormalizer.wireBody(testCase, NO_PAYLOAD, null, EMPTY));
    }

    @Test
    void doesNotMinifyNewlineDelimitedJsonMediaTypes() {
        byte[] body = bytes("{\"a\":1}\n{\"b\":2}\n");
        for (String type : new String[] {
                "application/x-ndjson",
                "application/jsonl",
                "application/json-seq",
                "application/jsonx",
                "text/plain"}) {
            assertArrayEquals(body, WireBodyNormalizer.normalize(body, type), type);
        }
    }

    @Test
    void minifiesJsonSubtypesSuffixesAndAwsProtocols() {
        byte[] body = bytes(" { \"n\": 1 } ");
        for (String type : new String[] {
                "application/json",
                "text/json; charset=utf-8",
                "application/vnd.api+json",
                "Application/X-Amz-Json-1.1",
                " application/json ;charset=utf-8"}) {
            assertArrayEquals(bytes("{\"n\":1}"), WireBodyNormalizer.normalize(body, type), type);
        }
    }

    private static byte[] wireBody(HttpResponseTestCase testCase, Schema schema, String defaultContentType) {
        return WireBodyNormalizer.wireBody(testCase, schema, defaultContentType, EMPTY);
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
