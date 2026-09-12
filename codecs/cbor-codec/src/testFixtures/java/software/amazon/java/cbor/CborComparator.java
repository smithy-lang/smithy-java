/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.java.cbor;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import software.amazon.smithy.java.cbor.Rpcv2CborCodec;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class CborComparator {

    private static final Rpcv2CborCodec CODEC = Rpcv2CborCodec.builder().build();

    // Compare doubles and floats with NaN tolerance: the default recursive comparison treats NaN as
    // unequal to NaN, and CBOR map key order is not significant, so payloads with NaN members must be
    // comparable through the decoded-document path.
    private static final RecursiveComparisonConfiguration CONFIG = RecursiveComparisonConfiguration.builder()
            .withComparatorForType(
                    (d1, d2) -> (Double.isNaN(d1) && Double.isNaN(d2)) ? 0 : Double.compare(d1, d2),
                    Double.class)
            .withComparatorForType(
                    (f1, f2) -> (Float.isNaN(f1) && Float.isNaN(f2)) ? 0 : Float.compare(f1, f2),
                    Float.class)
            .build();

    public static void assertEquals(ByteBuffer expected, ByteBuffer actual) {
        byte[] expectedBytes = ByteBufferUtils.getBytes(expected);
        byte[] actualBytes = ByteBufferUtils.getBytes(actual);
        if (Arrays.equals(expectedBytes, actualBytes)) {
            return;
        }
        Document expectedDoc = CODEC.createDeserializer(expectedBytes).readDocument();
        Document actualDoc = CODEC.createDeserializer(actualBytes).readDocument();
        assertThat(actualDoc)
                .usingRecursiveComparison(CONFIG)
                .isEqualTo(expectedDoc);
    }
}
