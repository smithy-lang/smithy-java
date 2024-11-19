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

        assertTrue(defaults.booleanMember());
        assertEquals(defaults.bigDecimal(), BigDecimal.valueOf(1.0));
        assertEquals(defaults.bigInteger(), BigInteger.valueOf(1));
        assertEquals(defaults.byteMember(), (byte) 1);
        assertEquals(defaults.doubleMember(), 1.0);
        assertEquals(defaults.floatMember(), 1f);
        assertEquals(defaults.integer(), 1);
        assertEquals(defaults.longMember(), 1);
        assertEquals(defaults.shortMember(), (short) 1);
        assertEquals(defaults.blob(), ByteBuffer.wrap(Base64.getDecoder().decode("YmxvYg==")));
        defaults.streamingBlob()
            .asByteBuffer()
            .thenAccept(b -> assertEquals(b, ByteBuffer.wrap(Base64.getDecoder().decode("c3RyZWFtaW5n"))));
        assertEquals(defaults.boolDoc(), Document.createBoolean(true));
        assertEquals(defaults.stringDoc(), Document.createString("string"));
        assertEquals(defaults.numberDoc(), Document.createInteger(1));
        assertEquals(defaults.floatingPointnumberDoc(), Document.createDouble(1.2));
        assertEquals(defaults.listDoc(), Document.createList(Collections.emptyList()));
        assertEquals(defaults.mapDoc(), Document.createStringMap(Collections.emptyMap()));
        assertEquals(defaults.list(), List.of());
        assertEquals(defaults.map(), Map.of());
        assertEquals(defaults.timestamp(), Instant.parse("1985-04-12T23:20:50.52Z"));
        assertEquals(defaults.enumMember(), NestedEnum.A);
        assertEquals(defaults.intEnum(), NestedIntEnum.A);
    }
}
