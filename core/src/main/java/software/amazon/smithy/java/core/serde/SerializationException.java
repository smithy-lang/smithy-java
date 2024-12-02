/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

/**
 * Exception throw by {@link ShapeSerializer} and {@link ShapeDeserializer} implementations when they fail
 * to serialize or deserialize a {@link software.amazon.smithy.java.core.schema.SerializableShape}.
 */
public class SerializationException extends RuntimeException {
    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(Throwable cause) {
        this(cause.getMessage(), cause);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
