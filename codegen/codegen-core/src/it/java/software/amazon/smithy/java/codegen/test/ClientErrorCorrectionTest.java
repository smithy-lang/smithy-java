/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.ClientErrorCorrectionInput;
import software.amazon.smithy.java.codegen.test.model.NestedEnum;
import software.amazon.smithy.java.codegen.test.model.NestedIntEnum;

public class ClientErrorCorrectionTest {

    @Test
    void correctsErrors() {
        var corrected = ClientErrorCorrectionInput.builder()
                .errorCorrection()
                .build();

        assertFalse(corrected.isBoolean());
        assertEquals(corrected.getBigDecimal(), BigDecimal.ZERO);
        assertEquals(corrected.getBigInteger(), BigInteger.ZERO);
        assertEquals(corrected.getByte(), (byte) 0);
        assertEquals(corrected.getDouble(), 0);
        assertEquals(corrected.getFloat(), 0);
        assertEquals(corrected.getInteger(), 0);
        assertEquals(corrected.getLong(), 0);
        assertEquals(corrected.getShort(), (short) 0);
        assertEquals(corrected.getBlob(), ByteBuffer.allocate(0));
        assertEquals(corrected.getStreamingBlob().contentLength(), 0);
        assertEquals(0, corrected.getStreamingBlob().asByteBuffer().remaining());
        assertNull(corrected.getDocument());
        assertEquals(corrected.getList(), List.of());
        assertEquals(corrected.getMap(), Map.of());
        assertEquals(corrected.getTimestamp(), Instant.EPOCH);
        assertEquals(corrected.getEnum().getType(), NestedEnum.Type.$UNKNOWN);
        assertEquals(corrected.getEnum().getValue(), "");
        assertEquals(corrected.getIntEnum().getType(), NestedIntEnum.Type.$UNKNOWN);
        assertEquals(corrected.getIntEnum().getValue(), 0);
    }
}
