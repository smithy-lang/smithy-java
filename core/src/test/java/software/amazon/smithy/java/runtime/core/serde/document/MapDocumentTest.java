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
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class MapDocumentTest {

    @Test
    public void createsDocument() {
        Map<Document, Document> entries = Map.of(
            Document.of("a"),
            Document.of(true),
            Document.of("b"),
            Document.of(false)
        );
        var map = Document.ofMap(entries);

        assertThat(map.type(), is(ShapeType.MAP));
        assertThat(map.asMap(), equalTo(entries));
        assertThat(Document.ofMap(map.asMap()), equalTo(map));
    }

    @Test
    public void serializesShape() {
        Map<Document, Document> entries = Map.of(
            Document.of("a"),
            Document.of(1),
            Document.of("b"),
            Document.of(2)
        );
        var map = Document.ofMap(entries);

        map.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(map));
            }
        });
    }

    @Test
    public void serializesContent() {
        Map<Document, Document> entries = Map.of(
            Document.of("a"),
            Document.of(1),
            Document.of("b"),
            Document.of(2)
        );
        var map = Document.ofMap(entries);

        var keys = new ArrayList<>();
        map.serializeContents(new SpecificShapeSerializer() {
            @Override
            public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
                assertThat(schema, equalTo(PreludeSchemas.DOCUMENT));
                consumer.accept(new MapSerializer() {
                    @Override
                    public void writeEntry(SdkSchema keySchema, String key, Consumer<ShapeSerializer> valueSerializer) {
                        keys.add(key);
                        valueSerializer.accept(new SpecificShapeSerializer() {
                            @Override
                            public void writeInteger(SdkSchema schema, int value) {
                                assertThat(schema, equalTo(PreludeSchemas.INTEGER));
                                if (key.equals("a")) {
                                    assertThat(value, is(1));
                                } else {
                                    assertThat(value, is(2));
                                }
                            }
                        });
                    }

                    @Override
                    public void writeEntry(SdkSchema keySchema, int key, Consumer<ShapeSerializer> valueSerializer) {
                        throw new UnsupportedOperationException("Expected a string key");
                    }

                    @Override
                    public void writeEntry(SdkSchema keySchema, long key, Consumer<ShapeSerializer> valueSerializer) {
                        throw new UnsupportedOperationException("Expected a string key");
                    }
                });
            }
        });

        assertThat(keys, containsInAnyOrder("a", "b"));
    }
}
