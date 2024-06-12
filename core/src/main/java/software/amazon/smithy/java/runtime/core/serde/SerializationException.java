/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import software.amazon.smithy.java.runtime.core.schema.ApiException;

public class SerializationException extends ApiException {
    public SerializationException(String message) {
        super(message, Fault.OTHER);
    }

    public SerializationException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause, Fault.OTHER);
    }
}
