/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class ListDocumentTest {

    @Test
    public void createsDocument() {
        List<Document> values = List.of(Document.of(1), Document.of(2));
        var document = Document.of(values);

        assertThat(document.type(), equalTo(ShapeType.LIST));
        assertThat(document.size(), is(2));
        assertThat(document.asList(), equalTo(values));
        assertThat(document, equalTo(Document.of(values)));
    }

    @Test
    public void serializesShape() {
        var document = Document.of(List.of(Document.of("a"), Document.of("b")));

        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(document));
            }
        });
    }

    @Test
    public void serializesContents() {
        List<Document> values = List.of(Document.of("a"), Document.of("b"));
        var document = Document.of(values);

        List<String> writtenStrings = new ArrayList<>();

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                value.serializeContents(this);
            }

            @Override
            public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
                assertThat(schema.type(), equalTo(ShapeType.LIST));
                consumer.accept(listState, new SpecificShapeSerializer() {
                    @Override
                    public void writeDocument(Schema schema, Document value) {
                        value.serializeContents(this);
                    }

                    @Override
                    public void writeString(Schema schema, String value) {
                        assertThat(schema, equalTo(PreludeSchemas.STRING));
                        writtenStrings.add(value);
                    }
                });
            }
        };

        document.serializeContents(serializer);

        assertThat(writtenStrings, contains("a", "b"));
    }

    @Test
    public void handlesNullValues() {
        List<Document> values = Arrays.asList(Document.of("a"), null);
        var document = Document.of(values);

        List<String> writtenStrings = new ArrayList<>();

        ShapeSerializer serializer = new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                value.serializeContents(this);
            }

            @Override
            public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
                assertThat(schema.type(), equalTo(ShapeType.LIST));
                consumer.accept(listState, new SpecificShapeSerializer() {
                    @Override
                    public void writeDocument(Schema schema, Document value) {
                        value.serializeContents(this);
                    }

                    @Override
                    public void writeString(Schema schema, String value) {
                        assertThat(schema, equalTo(PreludeSchemas.STRING));
                        writtenStrings.add(value);
                    }

                    @Override
                    public void writeNull(Schema schema) {
                        assertThat(schema, equalTo(PreludeSchemas.DOCUMENT));
                        writtenStrings.add(null);
                    }
                });
            }
        };

        document.serializeContents(serializer);

        assertThat(writtenStrings, contains("a", null));
    }
}
