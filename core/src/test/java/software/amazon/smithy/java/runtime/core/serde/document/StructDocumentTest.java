/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class StructDocumentTest {
    @Test
    public void createsDocument() {
        Map<String, Document> entries = new LinkedHashMap<>();
        entries.put("a", Document.of("a"));
        entries.put("b", Document.of(1));
        var document = Document.ofStruct(entries);

        assertThat(document.type(), equalTo(ShapeType.STRUCTURE));
        assertThat(document.getMember("a"), equalTo(Document.of("a")));
        assertThat(document.getMember("b"), equalTo(Document.of(1)));
    }

    @Test
    public void convertStructureToMap() {
        Map<String, Document> entries = new LinkedHashMap<>();
        entries.put("a", Document.of("a"));
        entries.put("b", Document.of(1));
        var document = Document.ofStruct(entries);

        assertThat(
            document.asMap(),
            equalTo(
                Map.of(
                    Document.of("a"),
                    Document.of("a"),
                    Document.of("b"),
                    Document.of(1)
                )
            )
        );
    }

    @Test
    public void serializesShape() {
        Map<String, Document> entries = new LinkedHashMap<>();
        entries.put("a", Document.of("a"));
        entries.put("b", Document.of(1));
        var document = Document.ofStruct(entries);

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContent() {
        Map<String, Document> entries = new LinkedHashMap<>();
        entries.put("a", Document.of("a"));
        entries.put("b", Document.of(1));
        var document = Document.ofStruct(entries);

        List<String> actions = new ArrayList<>();

        document.serializeContents(new SpecificShapeSerializer() {
            @Override
            public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
                assertThat(schema, equalTo(PreludeSchemas.DOCUMENT));
                actions.add("beginStruct");
                consumer.accept(ShapeSerializer.ofDelegatingConsumer((member, memberWriter) -> {
                    actions.add("member:" + member.memberName());
                    memberWriter.accept(new SpecificShapeSerializer() {
                        @Override
                        public void writeString(Schema schema1, String value) {
                            // Make sure the schema gave the appropriate member name and target.
                            assertThat(schema1.memberName(), equalTo("a"));
                            assertThat(schema1.memberTarget(), equalTo(PreludeSchemas.STRING));
                            actions.add("value:string:" + value);
                        }

                        @Override
                        public void writeInteger(Schema schema1, int value) {
                            // Make sure the schema gave the appropriate member name and target.
                            assertThat(schema1.memberName(), equalTo("b"));
                            assertThat(schema1.memberTarget(), equalTo(PreludeSchemas.INTEGER));
                            actions.add("value:integer:" + value);
                        }
                    });
                }));
                actions.add("endStruct");
            }
        });

        assertThat(
            actions,
            containsInAnyOrder(
                "beginStruct",
                "endStruct",
                "member:a",
                "member:b",
                "value:string:a",
                "value:integer:1"
            )
        );
    }
}
