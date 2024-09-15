/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

public class CborSerializerTest {
    @Test
    public void writesNull() {
        var codec = Rpcv2CborCodec.builder().build();
        var output = new ByteArrayOutputStream();
        var serializer = codec.createSerializer(output);
        serializer.writeNull(PreludeSchemas.STRING);
        serializer.flush();
        var result = output.toString(StandardCharsets.UTF_8);
        assertThat(result, equalTo("null"));
    }

    @Test
    public void writesNestedStructures() throws Exception {
        try (var codec = Rpcv2CborCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(
                    RpcV2CborTestData.BIRD,
                    SerializableStruct.create(RpcV2CborTestData.BIRD, (schema, ser) -> {
                        ser.writeStruct(schema.member("nested"), new NestedStruct());
                    })
                );
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"nested\":{\"number\":10}}"));
        }
    }

    @Test
    public void writesStructureUsingSerializableStruct() throws Exception {
        try (var codec = Rpcv2CborCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                serializer.writeStruct(RpcV2CborTestData.NESTED, new NestedStruct());
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"number\":10}"));
        }
    }

    @Test
    public void writesDunderTypeAndMoreMembers() throws Exception {
        var struct = new NestedStruct();
        var document = Document.createTyped(struct);
        try (var codec = Rpcv2CborCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\",\"number\":10}"));
        }
    }

    @Test
    public void writesNestedDunderType() throws Exception {
        var struct = new NestedStruct();
        var document = Document.createTyped(struct);
        var map = Document.createStringMap(Map.of("a", document));
        try (var codec = Rpcv2CborCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                map.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"a\":{\"__type\":\"smithy.example#Nested\",\"number\":10}}"));
        }
    }

    @Test
    public void writesDunderTypeForEmptyStruct() throws Exception {
        var struct = new EmptyStruct();
        var document = Document.createTyped(struct);
        try (var codec = Rpcv2CborCodec.builder().build(); var output = new ByteArrayOutputStream()) {
            try (var serializer = codec.createSerializer(output)) {
                document.serialize(serializer);
            }
            var result = output.toString(StandardCharsets.UTF_8);
            assertThat(result, equalTo("{\"__type\":\"smithy.example#Nested\"}"));
        }
    }

    private static final class NestedStruct implements SerializableStruct {
        @Override
        public void serialize(ShapeSerializer encoder) {
            encoder.writeStruct(RpcV2CborTestData.NESTED, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(RpcV2CborTestData.NESTED.member("number"), 10);
        }
    }

    private static final class EmptyStruct implements SerializableStruct {
        @Override
        public void serialize(ShapeSerializer encoder) {
            encoder.writeStruct(RpcV2CborTestData.NESTED, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}
    }
}
