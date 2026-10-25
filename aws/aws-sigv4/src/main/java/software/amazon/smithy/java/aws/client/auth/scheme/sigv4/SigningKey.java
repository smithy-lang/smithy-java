/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

final class SigningKey {
    private final byte[] signingKey;
    private final long date;

    SigningKey(byte[] signingKey, Instant instant) {
        this(signingKey, daysSinceEpoch(Objects.requireNonNull(instant, "instant must not be null")));
    }

    /**
     * @param daysSinceEpoch whole UTC days since 1970-01-01
     */
    SigningKey(byte[] signingKey, long daysSinceEpoch) {
        this.signingKey = Objects.requireNonNull(signingKey, "signingKey must not be null");
        this.date = daysSinceEpoch;
    }

    byte[] signingKey() {
        return signingKey;
    }

    boolean isValidFor(Instant other) {
        return date == daysSinceEpoch(other);
    }

    boolean isValidFor(long daysSinceEpoch) {
        return date == daysSinceEpoch;
    }

    private static long daysSinceEpoch(Instant instant) {
        return Instant.EPOCH.until(instant, ChronoUnit.DAYS);
    }
}
