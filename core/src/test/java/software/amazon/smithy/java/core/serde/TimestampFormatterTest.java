/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class TimestampFormatterTest {
    @Test
    public void testEpochSecondsRounding() {
        Instant wholeTime = Instant.ofEpochSecond(7234);
        assertEquals("7234", TimestampFormatter.Prelude.EPOCH_SECONDS.writeString(wholeTime));

        Instant fractionalTime = Instant.ofEpochMilli(1718830549174L);
        assertEquals("1718830549.174", TimestampFormatter.Prelude.EPOCH_SECONDS.writeString(fractionalTime));

        fractionalTime = Instant.ofEpochMilli(1718830549002L);
        assertEquals("1718830549.002", TimestampFormatter.Prelude.EPOCH_SECONDS.writeString(fractionalTime));
    }

    @Test
    public void readFromNumberPreservesMilliseconds() {
        var fmt = TimestampFormatter.Prelude.EPOCH_SECONDS;

        // BigDecimal with sub-second component: must keep the .803 (803 ms), not floor to
        // whole seconds. Regression for bd.longValue()*1000 truncating to seconds first.
        assertEquals(
                Instant.ofEpochMilli(1780359340803L),
                fmt.readFromNumber(new BigDecimal("1780359340.803")));
        assertEquals(
                Instant.ofEpochMilli(1718830549174L),
                fmt.readFromNumber(new BigDecimal("1718830549.174")));

        // Double fractional epoch-seconds preserved to millisecond resolution.
        assertEquals(
                Instant.ofEpochMilli(1718830549174L),
                fmt.readFromNumber(1718830549.174d));

        // Integer types: whole seconds.
        assertEquals(Instant.ofEpochSecond(7234), fmt.readFromNumber(7234));
        assertEquals(Instant.ofEpochSecond(7234), fmt.readFromNumber(7234L));
        assertEquals(Instant.ofEpochSecond(7234), fmt.readFromNumber(new BigInteger("7234")));

        // Negative fractional: -1.5s = epoch milli -1500.
        assertEquals(
                Instant.ofEpochMilli(-1500L),
                fmt.readFromNumber(new BigDecimal("-1.5")));
    }
}
