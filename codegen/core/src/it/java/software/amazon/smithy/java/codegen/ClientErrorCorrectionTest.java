/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.smithy.codegen.test.model.ClientErrorCorrectionInput;
import io.smithy.codegen.test.model.CouldBeEmptyStruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ClientErrorCorrectionTest {
    @Test
    void correctsErrors() throws IOException {
        var corrected = ClientErrorCorrectionInput.builder()
            .errorCorrection()
            .build();

        assertFalse(corrected.booleanMember());
        assertEquals(corrected.bigDecimal(), BigDecimal.ZERO);
        assertEquals(corrected.bigInteger(), BigInteger.ZERO);
        assertEquals(corrected.byteMember(), (byte) 0);
        assertEquals(corrected.doubleMember(), 0);
        assertEquals(corrected.floatMember(), 0);
        assertEquals(corrected.integer(), 0);
        assertEquals(corrected.longMember(), 0);
        assertEquals(corrected.shortMember(), (short) 0);
        assertArrayEquals(corrected.blob(), new byte[0]);
        assertEquals(corrected.streamingBlob().contentLength(), 0);
        assertArrayEquals(corrected.streamingBlob().inputStream().readAllBytes(), new byte[0]);
        assertNull(corrected.document());
        assertEquals(corrected.list(), List.of());
        assertEquals(corrected.map(), Map.of());
        assertEquals(corrected.timestamp(), Instant.EPOCH);
        assertEquals(corrected.structure(), CouldBeEmptyStruct.builder().build());
    }
}
