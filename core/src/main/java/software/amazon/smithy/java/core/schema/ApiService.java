/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Represents a modeled Smithy service.
 */
public interface ApiService {
    /**
     * Get the schema of the service.
     *
     * @return Returns the service schema, including the shape ID and relevant traits.
     */
    Schema schema();

    /**
     * Get an operation contained by this service by its shape ID.
     *
     * @throws IllegalArgumentException if the operation is not found in this service.
     */
    default ApiOperation<?, ?> getOperation(ShapeId id) {
        throw new IllegalArgumentException(
                String.format("Operation '%s' not found in service '%s'", id, schema().id()));
    }

    /**
     * Name of the Service.
     *
     * @return Returns the name of the service.
     */
    default String name() {
        return schema().id().getName();
    }
}
