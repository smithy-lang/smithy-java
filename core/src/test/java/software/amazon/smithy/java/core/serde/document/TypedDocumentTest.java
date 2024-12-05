/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class TypedDocumentTest {

    private SerializableShape createSerializableShape() {
        var structSchema = Schema.structureBuilder(ShapeId.from("smithy.example#Struct"))
            .putMember("a", PreludeSchemas.STRING)
            .putMember("b", PreludeSchemas.STRING)
            .build();

        return encoder -> {
            encoder.writeStruct(
                structSchema,
                new SerializableStruct() {
                    @Override
                    public Schema schema() {
                        return structSchema;
                    }

                    @Override
                    public void serializeMembers(ShapeSerializer s) {
                        s.writeString(schema().member("a"), "1");
                        s.writeString(schema().member("b"), "2");
                    }

                    @Override
                    public <T> T getMemberValue(Schema member) {
                        return null;
                    }
                }
            );
        };
    }

    @Test
    public void wrapsStructContentWithTypeAndSchema() {
        var serializableShape = createSerializableShape();
        var result = Document.of(serializableShape);

        assertThat(result.type(), equalTo(ShapeType.STRUCTURE));
        assertThat(
            result.toString(),
            equalTo(
                "StructureDocument[schema=Schema{id='smithy.example#Struct', type=structure}, members={a=StringDocument[schema=Schema{id='smithy.example#Struct$a', type=string}, value=1], b=StringDocument[schema=Schema{id='smithy.example#Struct$b', type=string}, value=2]}]"
            )
        );

        assertThat(result.getMember("a").type(), equalTo(ShapeType.STRING));
        assertThat(result.getMember("a").asString(), equalTo("1"));
        assertThat(result.getMember("b").type(), equalTo(ShapeType.STRING));
        assertThat(result.getMember("b").asString(), equalTo("2"));

        // Returns null when member not found.
        assertThat(result.getMember("X"), nullValue());

        // Equality and hashcode checks.
        assertThat(result, equalTo(result));
        assertThat(result, not(equalTo(null)));
        assertThat(result, not(equalTo("X")));
        assertThat(result, equalTo(Document.of(serializableShape)));
        assertThat(result.hashCode(), equalTo(Document.of(serializableShape).hashCode()));

        // Writes as document unless getting contents.
        result.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(result));
            }
        });

        // This is basically recreating the same document with the same captured schema.
        assertThat(result, equalTo(Document.of(result)));

        // Not equal because the left member has a schema with the same value, but the right has no schema.
        assertThat(result.getMember("a"), not(equalTo(Document.of("1"))));
        assertThat(result.getMember("b"), not(equalTo(Document.of("2"))));

        // Converts to a string map.
        var copy1 = result.asStringMap();
        assertThat(copy1.get("a").asString(), equalTo("1"));
        assertThat(copy1.get("b").asString(), equalTo("2"));
    }

    @Test
    public void getsSchemaValue() {
        var serializableShape = createSerializableShape();
        var result = (SerializableStruct) Document.of(serializableShape);
        var schema = result.schema();

        assertThat(result.getMemberValue(schema.member("a")), equalTo("1"));
        assertThat(result.getMemberValue(schema.member("b")), equalTo("2"));

        var bogus = Schema.createString(ShapeId.from("foo#Bar"));
        Assertions.assertThrows(IllegalArgumentException.class, () -> result.getMemberValue(bogus));
    }
}
