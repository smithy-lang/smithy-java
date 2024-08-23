/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

public class SigningKeyTest {
    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant EPOCH_PLUS_TWO_HOURS = Instant.EPOCH.plusSeconds(2 * 3600);
    private static final Instant EPOCH_PLUS_TWELVE_HOURS = Instant.EPOCH.plusSeconds(12 * 3600);
    private static final Instant EPOCH_PLUS_TWENTY_FIVE_HOURS = Instant.EPOCH.plusSeconds(25 * 3600);

    @Test
    void correctValidDate() {
        var key = new SigningKey("".getBytes(), EPOCH);
        assertTrue(key.isValidForDate(EPOCH_PLUS_TWO_HOURS));
        assertTrue(key.isValidForDate(EPOCH_PLUS_TWELVE_HOURS));
        assertFalse(key.isValidForDate(EPOCH_PLUS_TWENTY_FIVE_HOURS));
    }
}
