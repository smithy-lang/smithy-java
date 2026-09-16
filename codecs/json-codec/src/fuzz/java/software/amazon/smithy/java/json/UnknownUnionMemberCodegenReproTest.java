/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.smithy.SmithyJsonSerdeProvider;
import software.smithy.fuzz.test.model.UnionValue;

class UnknownUnionMemberCodegenReproTest {

    @Test
    void unknownUnionMemberSerializesSameAsInterpreted() {
        var interpreted = JsonCodec.builder().overrideSerdeProvider(new SmithyJsonSerdeProvider()).build();
        var codegen = JsonCodec.builder()
                .overrideSerdeProvider(new SmithyJsonSerdeProvider())
                .runtimeCodegen(true)
                .build();

        byte[] input = "{\"value\":{\"notARealMember\":-864441130}}".getBytes(StandardCharsets.UTF_8);
        var shape = interpreted.deserializeShape(input, UnionValue.builder());

        String viaInterpreted = new String(
                ByteBufferUtils.getBytes(interpreted.serialize(shape)),
                StandardCharsets.UTF_8);
        String viaCodegen = new String(
                ByteBufferUtils.getBytes(codegen.serialize(shape)),
                StandardCharsets.UTF_8);

        Assertions.assertEquals("{\"value\":{}}", viaInterpreted);
        Assertions.assertEquals(viaInterpreted, viaCodegen);
    }
}
