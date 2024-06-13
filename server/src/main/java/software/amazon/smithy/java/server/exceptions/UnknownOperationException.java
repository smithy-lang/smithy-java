/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.exceptions;

import software.amazon.smithy.java.runtime.core.schema.ApiException;

public final class UnknownOperationException extends ApiException {
    public UnknownOperationException(String unexpectedOperationName) {
        super("Unknown operation: " + unexpectedOperationName);
    }

    public UnknownOperationException() {
        super("Unknown operation");
    }
}
