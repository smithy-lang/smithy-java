/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import smithy.java.codegen.server.test.model.MyNestedStruct;
import smithy.java.codegen.server.test.model.MyStruct;
import smithy.java.codegen.server.test.model.MyUnion;
import smithy.java.codegen.server.test.model.NestedIntEnum;
import smithy.java.codegen.server.test.model.UsesOtherStructs;
import smithy.java.codegen.server.test.model.YesOrNo;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * General tests to check that generated type classes compile and are serializable
 */
public class GenericSerdeTest {

    static Stream<Arguments> types() {
        return Stream.of(
            Arguments.of(YesOrNo.YES, YesOrNo.builder()),
            Arguments.of(
                UsesOtherStructs.builder()
                    .other(
                        MyStruct.builder()
                            .fieldA("a")
                            .fieldB(
                                MyNestedStruct.builder().fieldC(1).fieldD(1f).build()
                            )
                            .build()
                    )
                    .nested(NestedIntEnum.ONE)
                    .build(),
                UsesOtherStructs.builder()
            ),
            Arguments.of(new MyUnion.OptionAMember("Value"), MyUnion.builder())
        );
    }

    @ParameterizedTest
    @MethodSource("types")
    <T extends SerializableShape> void serdeTest(T pojo, ShapeBuilder<T> builder) {
        var document = Document.createTyped(pojo);
        document.deserializeInto(builder);
        var output = builder.build();
        assertEquals(pojo.hashCode(), output.hashCode());
        assertEquals(pojo, output);
    }
}
