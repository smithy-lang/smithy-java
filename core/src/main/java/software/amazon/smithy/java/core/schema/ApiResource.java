/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a modeled Smithy resource.
 */
public interface ApiResource {
    /**
     * Get the schema of the resource.
     *
     * @return Returns the resource schema, including relevant traits.
     */
    Schema schema();

    /**
     * Map of property string names to Shape schemas that enumerate the identifiers of the resource.
     *
     * @return Map of the resource identifier schemas
     */
    default Map<String, Schema> identifiers() {
        return Collections.emptyMap();
    }

    /**
     * Map of property string names to Shape schemas that enumerate the properties of the resource.
     *
     * @return Map of the resource property schemas
     */
    default Map<String, Schema> properties() {
        return Collections.emptyMap();
    }

    /**
     * Get the service shape that the resource is contained within.
     *
     * @return the service shape at the root of the resource tree.
     */
    default ApiService service() {
        return null;
    }

    /**
     * Api Resource that this resource is bound to.
     *
     * <p>Note: Resources can be bound to only a single parent resource, although they may have multiple children.
     *
     * @return Resource this resource is bound to, or null.
     */
    default ApiResource boundResource() {
        return null;
    }
}
