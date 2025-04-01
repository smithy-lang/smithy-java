/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

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
}
