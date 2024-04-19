/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;


public class SerdeException extends RuntimeException {
    public SerdeException(String message) {
        super(message);
    }

    public SerdeException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public SerdeException(String message, Throwable cause) {
        super(message, cause);
    }
}
