/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
