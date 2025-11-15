/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import software.amazon.smithy.java.client.core.error.TransportException;

/**
 * A marker exception used to propagate a go-away event from the server.
 */
final class GoAwayException extends TransportException {
    private final long errorCode;

    public GoAwayException(long errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
