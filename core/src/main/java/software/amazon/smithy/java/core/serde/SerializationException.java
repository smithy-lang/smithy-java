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

    /**
     * Returns the given exception as a {@code SerializationException}, wrapping it if needed.
     *
     * <p>Used at deserialization entry points so that failures raised outside a deserializer (e.g. a shape builder
     * rejecting a union with more than one member set) are surfaced consistently.
     *
     * @param e Exception to convert.
     * @return Returns {@code e} if it is a {@code SerializationException}, or a new one wrapping it.
     */
    public static SerializationException wrap(RuntimeException e) {
        return e instanceof SerializationException se ? se : new SerializationException(e);
    }
}
