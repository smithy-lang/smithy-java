/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a SigningKey with a creation date to be stored in the {@code SigningCache}.
 *
 * <p>This key is considered valid for the same day on which it was created and is considered invalid for any other
 * day.
 */
final class SigningKey {
    private final byte[] signingKey;
    private final long date;

    SigningKey(byte[] signingKey, Instant instant) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
        this.date = daysSinceEpoch(Objects.requireNonNull(instant, "instant must not be null"));
    }

    byte[] signingKey() {
        return signingKey;
    }

    boolean isValidFor(Instant other) {
        return date == daysSinceEpoch(other);
    }

    private static long daysSinceEpoch(Instant instant) {
        return Duration.ofMillis(instant.toEpochMilli()).toDays();
    }

}
