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

    public ModeledApiException(Schema schema, String message) {
        super(message);
        this.schema = schema;
    }

    public ModeledApiException(Schema schema, String message, Fault errorType) {
        super(message, errorType);
        this.schema = schema;
    }

    public ModeledApiException(Schema schema, String message, Throwable cause) {
        super(message, cause);
        this.schema = schema;
    }

    public ModeledApiException(Schema schema, String message, Throwable cause, Fault errorType) {
        super(message, cause, errorType);
        this.schema = schema;
    }

    @Override
    public final Schema schema() {
        return schema;
    }
}
