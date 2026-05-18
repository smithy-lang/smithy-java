/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.java.cbor;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class CborComparator {

    private static final Rpcv2CborCodec CODEC = Rpcv2CborCodec.builder().build();

    public static void assertEquals(ByteBuffer expected, ByteBuffer actual) {
        byte[] expectedBytes = ByteBufferUtils.getBytes(expected);
        byte[] actualBytes = ByteBufferUtils.getBytes(actual);
        if (Arrays.equals(expectedBytes, actualBytes)) {
            return;
        }
        Document expectedDoc = CODEC.createDeserializer(expectedBytes).readDocument();
        Document actualDoc = CODEC.createDeserializer(actualBytes).readDocument();
        assertThat(actualDoc)
                .usingRecursiveComparison()
                .isEqualTo(expectedDoc);
    }
}
