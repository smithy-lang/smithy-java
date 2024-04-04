/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;

public class DocumentMapTest {
    @ParameterizedTest
    @MethodSource("mapProvider")
    public void serializesMaps(Map<Document, Document> mapOfXtoString) {
        var document = Document.ofMap(mapOfXtoString);
        Map<Document, Document> received = new HashMap<>();
        document.serialize(new SpecificShapeSerializer() {
            @Override
            public void beginMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
                consumer.accept(new MapSerializer() {
                    @Override
                    public void entry(String key, Consumer<ShapeSerializer> valueSerializer) {
                        valueSerializer.accept(new SpecificShapeSerializer() {
                            @Override
                            public void writeString(SdkSchema schema, String value) {
                                received.put(Document.of(key), Document.of(value));
                            }
                        });
                    }

                    @Override
                    public void entry(int key, Consumer<ShapeSerializer> valueSerializer) {
                        valueSerializer.accept(new SpecificShapeSerializer() {
                            @Override
                            public void writeString(SdkSchema schema, String value) {
                                received.put(Document.of(key), Document.of(value));
                            }
                        });
                    }

                    @Override
                    public void entry(long key, Consumer<ShapeSerializer> valueSerializer) {
                        valueSerializer.accept(new SpecificShapeSerializer() {
                            @Override
                            public void writeString(SdkSchema schema, String value) {
                                received.put(Document.of(key), Document.of(value));
                            }
                        });
                    }
                });
            }
        });

        assertThat(received, equalTo(mapOfXtoString));
    }

    public static List<Arguments> mapProvider() {
        return List.of(
            Arguments.of(Map.of(Document.of(1), Document.of("a"), Document.of(2), Document.of("b"))),
            Arguments.of(Map.of(Document.of(1L), Document.of("a"), Document.of(2L), Document.of("b"))),
            Arguments.of(Map.of(Document.of("1"), Document.of("a"), Document.of("2"), Document.of("b")))
        );
    }
}
