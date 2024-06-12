/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * The top-level exception that should be used to throw modeled errors from clients and servers.
 */
public abstract class ModeledApiException extends ApiException implements SerializableStruct {

    private final ShapeId shapeId;

    public ModeledApiException(ShapeId shapeId, String message) {
        super(message);
        this.shapeId = shapeId;
    }

    public ModeledApiException(ShapeId shapeId, String message, Fault errorType) {
        super(message, errorType);
        this.shapeId = shapeId;
    }

    public ModeledApiException(ShapeId shapeId, String message, Throwable cause) {
        super(message, cause);
        this.shapeId = shapeId;
    }

    public ModeledApiException(ShapeId shapeId, String message, Throwable cause, Fault errorType) {
        super(message, cause, errorType);
        this.shapeId = shapeId;
    }

    /**
     * Get the shape ID of error.
     *
     * @return Returns the error shape ID.
     */
    public final ShapeId getShapeId() {
        return shapeId;
    }
}
