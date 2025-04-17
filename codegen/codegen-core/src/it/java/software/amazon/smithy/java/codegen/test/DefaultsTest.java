/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.DefaultsInput;
import software.amazon.smithy.java.codegen.test.model.NestedEnum;
import software.amazon.smithy.java.codegen.test.model.NestedIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;

public class DefaultsTest {
    @Test
    void setsCorrectDefault() {
        var defaults = DefaultsInput.builder().build();

        assertTrue(defaults.getBooleanMember());
        assertEquals(defaults.getBigDecimal(), BigDecimal.valueOf(1.0));
        assertEquals(defaults.getBigInteger(), BigInteger.valueOf(1));
        assertEquals(defaults.getByteMember(), (byte) 1);
        assertEquals(defaults.getDoubleMember(), 1.0);
        assertEquals(defaults.getFloatMember(), 1f);
        assertEquals(defaults.getInteger(), 1);
        assertEquals(defaults.getLongMember(), 1);
        assertEquals(defaults.getShortMember(), (short) 1);
        assertEquals(defaults.getBlob(), ByteBuffer.wrap(Base64.getDecoder().decode("YmxvYg==")));
        defaults.getStreamingBlob()
                .asByteBuffer()
                .thenAccept(b -> assertEquals(b, ByteBuffer.wrap(Base64.getDecoder().decode("c3RyZWFtaW5n"))));
        assertEquals(defaults.getBoolDoc(), Document.of(true));
        assertEquals(defaults.getStringDoc(), Document.of("string"));
        assertEquals(defaults.getNumberDoc(), Document.of(1));
        assertEquals(defaults.getFloatingPointnumberDoc(), Document.of(1.2));
        assertEquals(defaults.getListDoc(), Document.of(Collections.emptyList()));
        assertEquals(defaults.getMapDoc(), Document.of(Collections.emptyMap()));
        assertEquals(defaults.getList(), List.of());
        assertEquals(defaults.getMap(), Map.of());
        assertEquals(defaults.getTimestamp(), Instant.parse("1985-04-12T23:20:50.52Z"));
        assertEquals(defaults.getEnumMember(), NestedEnum.A);
        assertEquals(defaults.getIntEnum(), NestedIntEnum.A);
    }
}
