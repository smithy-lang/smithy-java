/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api.identity;

/**
 * Signals that refreshing an identity is not expected to succeed without external action.
 *
 * <p>Caching resolvers propagate this exception from on-demand resolutions and do not apply refresh backoff.
 * Background refreshes retain cached identities and retry on the next on-demand resolution.
 */
public class NonRecoverableIdentityException extends RuntimeException {

    public NonRecoverableIdentityException(String message) {
        super(message);
    }

    public NonRecoverableIdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
