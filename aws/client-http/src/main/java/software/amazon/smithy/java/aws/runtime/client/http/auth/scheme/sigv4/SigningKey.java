/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a SigningKey with an expiration time to be stored in the {@code SigningCache}.
 */
final class SigningKey {
    private final byte[] signingKey;
    private final long date;

    public SigningKey(byte[] signingKey, Instant instant) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
        this.date = daysSinceEpoch(Objects.requireNonNull(instant, "instant must not be null"));
    }

    public boolean isValidForDate(Instant other) {
        return date == daysSinceEpoch(other);
    }

    private static long daysSinceEpoch(Instant instant) {
        return Duration.ofMillis(instant.toEpochMilli()).toDays();
    }

    public byte[] signingKey() {
        return signingKey;
    }
}
