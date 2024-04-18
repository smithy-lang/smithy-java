/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class TypedDocumentTest {
    @Test
    public void requiresSchemaToEmitStruct() {
        var e = Assertions.assertThrows(
            SdkSerdeException.class,
            () -> Document.ofStruct(encoder -> encoder.writeString(PreludeSchemas.STRING, "A"))
        );

        assertThat(e.getMessage(), containsString("Typed documents can only wrap structures"));
    }

    @Test
    public void requiresSchemaToEmitSomething() {
        var e = Assertions.assertThrows(SdkSerdeException.class, () -> Document.ofStruct(encoder -> {}));

        assertThat(e.getMessage(), containsString("When attempting to create a typed document"));
    }

    @Test
    public void wrapsStructContentWithTypeAndSchema() {
        SerializableShape serializableShape = encoder -> {
            encoder.writeStruct(PreludeSchemas.DOCUMENT, s -> {
                var aMember = SdkSchema.memberBuilder(-1, "a", PreludeSchemas.STRING)
                    .id(PreludeSchemas.DOCUMENT.id())
                    .build();
                s.writeString(aMember, "1");
                var bMember = SdkSchema.memberBuilder(-1, "b", PreludeSchemas.STRING)
                    .id(PreludeSchemas.DOCUMENT.id())
                    .build();
                s.writeString(bMember, "2");
            });
        };

        var result = Document.ofStruct(serializableShape);

        assertThat(result.type(), equalTo(ShapeType.DOCUMENT));
        assertThat(
            result.toString(),
            equalTo(
                "TypedDocument{schema=SdkSchema{id='smithy.api#Document', type=document}, members={a=SdkSchema{id='smithy.api#Document$a', type=string}}, {b=SdkSchema{id='smithy.api#Document$b', type=string}}}"
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
        assertThat(result, equalTo(Document.ofStruct(serializableShape)));
        assertThat(result.hashCode(), equalTo(Document.ofStruct(serializableShape).hashCode()));

        // Writes as document unless getting contents.
        result.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(result));
            }
        });

        var copy1 = Document.ofValue(result);
        var copy2 = Document.ofValue(result);
        assertThat(copy1, equalTo(copy2));

        assertThat(copy1.getMember("a"), equalTo(Document.of("1")));
        assertThat(copy1.getMember("b"), equalTo(Document.of("2")));
    }
}
