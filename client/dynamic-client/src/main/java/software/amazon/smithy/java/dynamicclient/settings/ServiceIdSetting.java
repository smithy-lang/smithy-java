/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.settings;

import java.util.Objects;
import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ToShapeId;

/**
 * Sets the required Smithy service shape ID.
 */
public interface ServiceIdSetting<B extends ClientSetting<B>> extends ClientSetting<B> {
    /**
     * Smithy model used with a client.
     */
    Context.Key<ShapeId> SERVICE_ID = Context.key("Smithy service shape ID");

    /**
     * Set the Smithy service shape ID to use with the dynamic client.
     *
     * @param shape Smithy service shape ID.
     */
    default B serviceId(ToShapeId shape) {
        return putConfig(SERVICE_ID, Objects.requireNonNull(shape, "service cannot be null").toShapeId());
    }
}
