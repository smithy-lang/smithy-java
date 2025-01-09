/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.Collections;
import java.util.List;
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
     * Lifecycle operation used to create the resource.
     *
     * @return Schema of the create lifecycle operation, or null
     */
    default Schema create() {
        return null;
    }

    /**
     * Idempotent lifecycle operation used to create the resource.
     *
     * @return Schema of the put lifecycle operation, or null.
     */
    default Schema put() {
        return null;
    }

    /**
     * Lifecycle operation used to read the resource.
     *
     * @return Schema of the read lifecycle operation, or null
     */
    default Schema read() {
        return null;
    }

    /**
     * Lifecycle operation used to create the resource.
     *
     * @return Schema of the update lifecycle operation, or null
     */
    default Schema update() {
        return null;
    }

    /**
     * Lifecycle operation used to delete the resource.
     *
     * @return Schema of the delete lifecycle operation, or null
     */
    default Schema delete() {
        return null;
    }

    /**
     * Lifecycle operation used to list resources of this type.
     *
     * @return Schema of the list lifecycle operation, or null
     */
    default Schema list() {
        return null;
    }

    /**
     * Non-lifecycle collection operations bound to the resource, if any.
     *
     * @return list of bound non-lifecycle collection operation schemas.
     */
    default List<Schema> collectionOperations() {
        return Collections.emptyList();
    }

    /**
     * Non-lifecycle instance operations bound to the resource, if any.
     *
     * @return list of bound non-lifecycle operation schemas.
     */
    default List<Schema> operations() {
        return Collections.emptyList();
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
