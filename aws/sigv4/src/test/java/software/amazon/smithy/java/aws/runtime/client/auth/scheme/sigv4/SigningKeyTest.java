/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.auth.scheme.sigv4;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class SigningKeyTest {
    private static final Instant EPOCH = Instant.EPOCH;
    private static final Instant EPOCH_PLUS_TWO_HOURS = Instant.EPOCH.plus(Duration.ofHours(2));
    private static final Instant EPOCH_PLUS_TWELVE_HOURS = Instant.EPOCH.plus(Duration.ofHours(12));
    private static final Instant EPOCH_PLUS_TWENTY_FIVE_HOURS = Instant.EPOCH.plus(Duration.ofHours(25));
    private static final Instant EPOCH_MINUS_ONE_DAY = Instant.EPOCH.minus(Duration.ofDays(1));

    @Test
    void correctValidDate() {
        var key = new SigningKey("".getBytes(), EPOCH);
        assertTrue(key.isValidFor(EPOCH_PLUS_TWO_HOURS));
        assertTrue(key.isValidFor(EPOCH_PLUS_TWELVE_HOURS));
        assertFalse(key.isValidFor(EPOCH_PLUS_TWENTY_FIVE_HOURS));
        assertFalse(key.isValidFor(EPOCH_MINUS_ONE_DAY));
    }
}
