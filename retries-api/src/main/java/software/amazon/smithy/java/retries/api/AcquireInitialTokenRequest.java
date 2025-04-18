/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.api;

/**
 * Encapsulates the abstract scope to start the attempts about to be executed using a retry strategy.
 *
 * @param scope An abstract scope for the attempts about to be executed.
 *              <p>A scope should be a unique string describing the smallest possible scope of failure for the attempts
 *              about to be executed. In practical terms, this is a key for the token bucket used to throttle request
 *              attempts. All attempts with the same scope share the same token bucket within the same
 *              {@link RetryStrategy}, ensuring that token-bucket throttling for requests against one resource do not
 *              result in throttling for requests against other, unrelated resources.
 */
public record AcquireInitialTokenRequest(String scope) {}
