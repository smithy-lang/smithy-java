/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class TypedDocumentTest {

    private SerializableShape createSerializableShape() {
        var structSchema = Schema.builder()
            .id("smithy.example#Struct")
            .type(ShapeType.STRUCTURE)
            .members(
                Schema.memberBuilder("a", PreludeSchemas.STRING),
                Schema.memberBuilder("b", PreludeSchemas.STRING)
            )
            .build();

        return encoder -> {
            encoder.writeStruct(structSchema, SerializableStruct.create(structSchema, (schema, s) -> {
                s.writeString(schema.member("a"), "1");
                s.writeString(schema.member("b"), "2");
            }));
        };
    }

    @Test
    public void wrapsStructContentWithTypeAndSchema() {
        var serializableShape = createSerializableShape();
        var result = Document.createTyped(serializableShape);

        assertThat(result.type(), equalTo(ShapeType.STRUCTURE));
        assertThat(
            result.toString(),
            equalTo(
                "StructureDocument[schema=SdkSchema{id='smithy.example#Struct', type=structure}, members={a=StringDocument[schema=SdkSchema{id='smithy.example#Struct$a', type=string}, value=1], b=StringDocument[schema=SdkSchema{id='smithy.example#Struct$b', type=string}, value=2]}]"
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
        assertThat(result, equalTo(Document.createTyped(serializableShape)));
        assertThat(result.hashCode(), equalTo(Document.createTyped(serializableShape).hashCode()));

        // Writes as document unless getting contents.
        result.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(result));
            }
        });

        // This is basically recreating the same document with the same captured schema.
        assertThat(result, equalTo(Document.createTyped(result)));

        // Not equal because the left member has a schema with the same value, but the right has no schema.
        assertThat(result.getMember("a"), not(equalTo(Document.createString("1"))));
        assertThat(result.getMember("b"), not(equalTo(Document.createString("2"))));

        // Converts to a string map.
        var copy1 = result.asStringMap();
        assertThat(copy1.get("a").asString(), equalTo("1"));
        assertThat(copy1.get("b").asString(), equalTo("2"));
    }
}
