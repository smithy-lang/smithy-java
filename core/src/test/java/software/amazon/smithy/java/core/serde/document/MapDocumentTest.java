/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

public class MapDocumentTest {

    @Test
    public void createsDocument() {
        Map<String, Document> entries = Map.of(
                "a",
                Document.of(true),
                "b",
                Document.of(false));
        var map = Document.of(entries);

        assertThat(map.type(), is(ShapeType.MAP));
        assertThat(map.size(), is(2));
        assertThat(map.asStringMap(), equalTo(entries));
        assertThat(Document.of(map.asStringMap()), equalTo(map));
    }

    @Test
    public void serializesShape() {
        Map<String, Document> entries = Map.of("a", Document.of(1), "b", Document.of(2));
        var map = Document.of(entries);

        map.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                assertThat(value, is(map));
            }
        });
    }

    @Test
    public void serializesContent() {
        Map<String, Document> entries = Map.of("a", Document.of(1), "b", Document.of(2));
        var map = Document.of(entries);

        var keys = new ArrayList<>();
        map.serializeContents(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(Schema schema, Document value) {
                value.serializeContents(this);
            }

            @Override
            public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
                assertThat(schema.type(), equalTo(ShapeType.MAP));
                consumer.accept(state, new MapSerializer() {
                    @Override
                    public <K> void writeEntry(
                            Schema keySchema,
                            String key,
                            K mapState,
                            BiConsumer<K, ShapeSerializer> valueSerializer
                    ) {
                        keys.add(key);
                        valueSerializer.accept(mapState, new SpecificShapeSerializer() {
                            @Override
                            public void writeDocument(Schema schema, Document value) {
                                value.serializeContents(this);
                            }

                            @Override
                            public void writeInteger(Schema schema, int value) {
                                assertThat(schema, equalTo(PreludeSchemas.INTEGER));
                                if (key.equals("a")) {
                                    assertThat(value, is(1));
                                } else {
                                    assertThat(value, is(2));
                                }
                            }
                        });
                    }
                });
            }
        });

        assertThat(keys, containsInAnyOrder("a", "b"));
    }
}
