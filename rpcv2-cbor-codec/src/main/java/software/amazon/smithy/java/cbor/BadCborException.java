/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import software.amazon.smithy.java.core.serde.SerializationException;

final class BadCborException extends SerializationException {
    BadCborException(String message) {
        this(message, false);
    }

    BadCborException(String message, boolean includeTrace) {
        super(message);
        if (includeTrace) {
            super.fillInStackTrace();
        }
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
