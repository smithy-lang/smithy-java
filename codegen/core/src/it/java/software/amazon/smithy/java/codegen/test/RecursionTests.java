/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.codegen.test.model.IntermediateListStructure;
import software.amazon.smithy.java.codegen.test.model.IntermediateMapStructure;
import software.amazon.smithy.java.codegen.test.model.SelfReferencing;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.json.JsonCodec;

public class RecursionTests {

    static Stream<SerializableShape> recursiveTypeSource() {
        return Stream.of(
            SelfReferencing.builder()
                .self(SelfReferencing.builder().self(SelfReferencing.builder().build()).build())
                .build(),
            IntermediateListStructure.builder()
                .foo(
                    List.of(
                        IntermediateListStructure.builder()
                            .foo(
                                List.of(IntermediateListStructure.builder().build())
                            )
                            .build()
                    )
                )
                .build(),
            IntermediateMapStructure.builder()
                .foo(
                    Map.of(
                        "a",
                        IntermediateMapStructure.builder()
                            .foo(
                                Map.of("b", IntermediateMapStructure.builder().build())
                            )
                            .build()
                    )
                )
                .build()
        );
    }

    @ParameterizedTest
    @MethodSource("recursiveTypeSource")
    void pojoToDocumentRoundTrip(SerializableStruct pojo) {
        var output = Utils.pojoToDocumentRoundTrip(pojo);
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }


    static Stream<Arguments> recursiveJsonSource() {
        return Stream.of(
            Arguments.of("{\"self\":{\"self\":{}}}", SelfReferencing.builder()),
            Arguments.of("{\"foo\":[{\"foo\":[{\"foo\":[]}]}]}", IntermediateListStructure.builder()),
            Arguments.of(
                "{\"foo\":{\"a\":{\"foo\":{}},\"b\":{\"foo\":{\"c\":{\"foo\":{}}}}}}",
                IntermediateMapStructure.builder()
            )
        );
    }

    @ParameterizedTest
    @MethodSource("recursiveJsonSource")
    <B extends ShapeBuilder<T>, T extends SerializableShape> void jsonDeserializationOfSelfReferencing(
        String json,
        B builder
    ) {
        try (var codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build()) {
            var output = codec.deserializeShape(json, builder);
            var serialized = codec.serializeToString(output);
            assertEquals(serialized, json);
        }
    }
}
