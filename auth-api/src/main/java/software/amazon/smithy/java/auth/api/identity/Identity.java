/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

import java.time.Instant;

/**
 * Interface to represent the identity of the caller, used for authentication.
 */
public interface Identity {
    /**
     * The time after which this identity will no longer be valid. If this is null, an expiration time is not known
     * (but the identity may still expire at some time in the future).
     *
     * @return the expiration time, if known, or null.
     */
    default Instant expirationTime() {
        return null;
    }
}
