/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.retries.api;

/**
 * Exception thrown by {@link RetryStrategy} when a new token cannot be acquired.
 */
public final class TokenAcquisitionFailedException extends RuntimeException {
    private final transient RetryToken token;

    public TokenAcquisitionFailedException(String msg) {
        super(msg);
        token = null;
    }

    public TokenAcquisitionFailedException(String msg, Throwable cause) {
        super(msg, cause);
        token = null;
    }

    public TokenAcquisitionFailedException(String msg, RetryToken token, Throwable cause) {
        super(msg, cause);
        this.token = token;
    }

    /**
     * Returns the retry token that tracked the execution.
     * @return the retry token.
     */
    public RetryToken token() {
        return token;
    }
}
