/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.error;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;

/**
 * The top-level exception that should be used to throw modeled errors from clients and servers.
 */
public abstract class ModeledException extends CallException implements SerializableStruct {

    private final Schema schema;
    private boolean deserialized = false;

    protected ModeledException(Schema schema, String message) {
        super(message);
        this.schema = schema;
    }

    protected ModeledException(Schema schema, String message, ErrorFault errorType) {
        super(message, null, errorType, null);
        this.schema = schema;
    }

    protected ModeledException(Schema schema, String message, ErrorFault errorType, Throwable cause) {
        super(message, cause, errorType, null);
        this.schema = schema;
    }

    protected ModeledException(
            Schema schema,
            String message,
            ErrorFault errorType,
            Throwable cause,
            Boolean captureStackTrace,
            boolean deserialized
    ) {
        super(message, cause, errorType, captureStackTrace);
        this.schema = schema;
        this.deserialized = deserialized;
    }

    protected ModeledException(
            Schema schema,
            String message,
            Throwable cause,
            Boolean captureStackTrace,
            boolean deserialized
    ) {
        super(message, cause, captureStackTrace);
        this.schema = schema;
        this.deserialized = deserialized;
    }

    protected ModeledException(Schema schema, String message, Throwable cause) {
        super(message, cause, (Boolean) null);
        this.schema = schema;
    }

    protected ModeledException(
            Schema schema,
            String message,
            Throwable cause,
            ErrorFault errorType,
            Boolean captureStackTrace,
            boolean deserialized
    ) {
        super(message, cause, errorType, captureStackTrace);
        this.schema = schema;
        this.deserialized = deserialized;
    }

    @Override
    public final Schema schema() {
        return schema;
    }

    /**
     * Get the status code of an error from its schema.
     *
     * @param schema Schema to check for the httpError and error trait.
     * @return the resolved status code, or 500.
     */
    public static int getHttpStatusCode(Schema schema) {
        var httpError = schema.getTrait(TraitKey.HTTP_ERROR_TRAIT);
        if (httpError != null) {
            return httpError.getCode();
        }
        var errorTrait = schema.getTrait(TraitKey.ERROR_TRAIT);
        if (errorTrait != null) {
            return errorTrait.getDefaultHttpStatusCode();
        }
        return 500;
    }

    /**
     * Check if the error was deserialized from a response.
     *
     * <p>Errors deserialized from a response should not be re-serialized by servers.
     * Instead, these errors should be treated as an internal failures.
     *
     * @return true if the error was created by deserializing a response.
     */
    public boolean deserialized() {
        return deserialized;
    }
}
