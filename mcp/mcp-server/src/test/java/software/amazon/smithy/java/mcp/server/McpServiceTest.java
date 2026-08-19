/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;

class McpServiceTest {

    private McpService service;

    @BeforeEach
    void setUp() {
        service = new McpService(
                Collections.emptyMap(),
                List.of(),
                "test",
                "1.0",
                null,
                null,
                null
        );
    }

    @Test
    void testAdaptOutputBlobFromBinaryDocument() {
        var schema = Schema.createBlob(ShapeId.from("smithy.test#Blob"));
        var bytes = "Hello, World!".getBytes(StandardCharsets.UTF_8);

        var result = service.adaptOutputDocument(Document.of(bytes), schema);

        assertEquals(Base64.getEncoder().encodeToString(bytes), result.asString());
    }

    @Test
    void testAdaptOutputBlobFromStringDocument() {
        var schema = Schema.createBlob(ShapeId.from("smithy.test#Blob"));
        var base64 = Base64.getEncoder().encodeToString("payload".getBytes(StandardCharsets.UTF_8));

        var result = service.adaptOutputDocument(Document.of(base64), schema);

        assertEquals(base64, result.asString());
    }

    @Test
    void testAdaptOutputBlobFromUnsupportedDocumentTypeThrows() {
        var schema = Schema.createBlob(ShapeId.from("smithy.test#Blob"));

        assertThrows(Exception.class, () -> service.adaptOutputDocument(Document.of(42), schema));
    }

    @Test
    void testAdaptOutputNullReturnsNull() {
        var schema = Schema.createBlob(ShapeId.from("smithy.test#Blob"));

        assertNull(service.adaptOutputDocument(null, schema));
    }

    @Test
    void testAdaptOutputStructureWithBlobFields() {
        var blobSchema = Schema.createBlob(ShapeId.from("smithy.test#Blob"));
        var structSchema = Schema.structureBuilder(ShapeId.from("smithy.test#Output"))
                .putMember("binaryBlob", blobSchema)
                .putMember("stringBlob", blobSchema)
                .build();

        var bytes = "binary content".getBytes(StandardCharsets.UTF_8);
        var base64 = Base64.getEncoder().encodeToString("string content".getBytes(StandardCharsets.UTF_8));
        var doc = Document.of(Map.of(
                "binaryBlob", Document.of(bytes),
                "stringBlob", Document.of(base64)));

        var result = service.adaptOutputDocument(doc, structSchema);

        assertEquals(Base64.getEncoder().encodeToString(bytes), result.getMember("binaryBlob").asString());
        assertEquals(base64, result.getMember("stringBlob").asString());
    }
}
