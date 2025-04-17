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

        assertFalse(corrected.getBooleanMember());
        assertEquals(corrected.getBigDecimal(), BigDecimal.ZERO);
        assertEquals(corrected.getBigInteger(), BigInteger.ZERO);
        assertEquals(corrected.getByteMember(), (byte) 0);
        assertEquals(corrected.getDoubleMember(), 0);
        assertEquals(corrected.getFloatMember(), 0);
        assertEquals(corrected.getInteger(), 0);
        assertEquals(corrected.getLongMember(), 0);
        assertEquals(corrected.getShortMember(), (short) 0);
        assertEquals(corrected.getBlob(), ByteBuffer.allocate(0));
        assertEquals(corrected.getStreamingBlob().contentLength(), 0);
        corrected.getStreamingBlob().asByteBuffer().thenAccept(bytes -> assertEquals(0, bytes.remaining()));
        assertNull(corrected.getDocument());
        assertEquals(corrected.getList(), List.of());
        assertEquals(corrected.getMap(), Map.of());
        assertEquals(corrected.getTimestamp(), Instant.EPOCH);
        assertEquals(corrected.getEnumMember().getType(), NestedEnum.Type.$UNKNOWN);
        assertEquals(corrected.getEnumMember().getValue(), "");
        assertEquals(corrected.getIntEnum().getType(), NestedIntEnum.Type.$UNKNOWN);
        assertEquals(corrected.getIntEnum().getValue(), 0);
    }
}
