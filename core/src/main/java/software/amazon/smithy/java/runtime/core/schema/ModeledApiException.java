/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

/**
 * The top-level exception that should be used to throw modeled errors from clients and servers.
 */
public abstract class ModeledApiException extends ApiException implements SerializableStruct {

    private final Schema schema;

    protected ModeledApiException(Schema schema, String message) {
        super(message);
        this.schema = schema;
    }

    protected ModeledApiException(Schema schema, String message, Fault errorType) {
        super(message, null, errorType, null);
        this.schema = schema;
    }

    protected ModeledApiException(Schema schema, String message, Fault errorType, Throwable cause) {
        super(message, cause, errorType, null);
        this.schema = schema;
    }

    protected ModeledApiException(
        Schema schema,
        String message,
        Fault errorType,
        Throwable cause,
        Boolean captureStackTrace
    ) {
        super(message, cause, errorType, captureStackTrace);
        this.schema = schema;
    }

    protected ModeledApiException(Schema schema, String message, Throwable cause, Boolean captureStackTrace) {
        super(message, cause, captureStackTrace);
        this.schema = schema;
    }

    protected ModeledApiException(Schema schema, String message, Throwable cause) {
        super(message, cause, (Boolean) null);
        this.schema = schema;
    }

    protected ModeledApiException(
        Schema schema,
        String message,
        Throwable cause,
        Fault errorType,
        Boolean captureStackTrace
    ) {
        super(message, cause, errorType, captureStackTrace);
        this.schema = schema;
    }

    @Override
    public final Schema schema() {
        return schema;
    }
}
