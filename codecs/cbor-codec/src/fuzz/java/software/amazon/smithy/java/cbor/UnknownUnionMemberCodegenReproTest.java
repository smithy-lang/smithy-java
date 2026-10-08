/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.HexFormat;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.smithy.fuzz.test.model.UnionValue;

class UnknownUnionMemberCodegenReproTest {

    @Test
    void unknownUnionMemberSerializesSameAsInterpreted() {
        var interpreted = Rpcv2CborCodec.builder().runtimeCodegen(false).build();
        var codegen = Rpcv2CborCodec.builder().runtimeCodegen(true).build();

        // {"value":{"notARealMember":-864441130}}
        byte[] input = HexFormat.of().parseHex("a16576616c7565a16e6e6f74415265616c4d656d6265723a33865329");
        var shape = interpreted.deserializeShape(input, UnionValue.builder());

        byte[] viaInterpreted = ByteBufferUtils.getBytes(interpreted.serialize(shape));
        byte[] viaCodegen = ByteBufferUtils.getBytes(codegen.serialize(shape));

        // {"value":{}}
        Assertions.assertArrayEquals(HexFormat.of().parseHex("bf6576616c7565bfffff"), viaInterpreted);
        Assertions.assertArrayEquals(viaInterpreted, viaCodegen);
    }
}
