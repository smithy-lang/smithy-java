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
import java.util.function.BiConsumer;
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
        Map<String, Document> entries = Map.of(
            "a",
            Document.createBoolean(true),
            "b",
            Document.createBoolean(false)
        );
        var map = Document.createStringMap(entries);

        assertThat(map.type(), is(ShapeType.MAP));
        assertThat(map.asStringMap(), equalTo(entries));
        assertThat(Document.createStringMap(map.asStringMap()), equalTo(map));
    }

    @Test
    public void serializesShape() {
        Map<String, Document> entries = Map.of("a", Document.createInteger(1), "b", Document.createInteger(2));
        var map = Document.createStringMap(entries);

        map.serialize(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                assertThat(value, is(map));
            }
        });
    }

    @Test
    public void serializesContent() {
        Map<String, Document> entries = Map.of("a", Document.createInteger(1), "b", Document.createInteger(2));
        var map = Document.createStringMap(entries);

        var keys = new ArrayList<>();
        map.serializeContents(new SpecificShapeSerializer() {
            @Override
            public void writeDocument(SdkSchema schema, Document value) {
                value.serializeContents(this);
            }

            @Override
            public <T> void writeMap(SdkSchema schema, T state, BiConsumer<T, MapSerializer> consumer) {
                assertThat(schema.type(), equalTo(ShapeType.MAP));
                consumer.accept(state, new MapSerializer() {
                    @Override
                    public <K> void writeEntry(
                        SdkSchema keySchema,
                        String key,
                        K mapState,
                        BiConsumer<K, ShapeSerializer> valueSerializer
                    ) {
                        keys.add(key);
                        valueSerializer.accept(mapState, new SpecificShapeSerializer() {
                            @Override
                            public void writeDocument(SdkSchema schema, Document value) {
                                value.serializeContents(this);
                            }

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
                });
            }
        });

        assertThat(keys, containsInAnyOrder("a", "b"));
    }
}
