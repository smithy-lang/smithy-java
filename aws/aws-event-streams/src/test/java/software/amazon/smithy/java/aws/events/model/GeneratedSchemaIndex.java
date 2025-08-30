/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Generated SchemaIndex implementation that provides access to all schemas in the model.
 */
public final class GeneratedSchemaIndex extends SchemaIndex {

    private static final Map<ShapeId, Schema> SCHEMA_MAP = new HashMap<>();

    static {
        SCHEMA_MAP.put(Schemas.BLOB_EVENT.id(), Schemas.BLOB_EVENT);
        SCHEMA_MAP.put(Schemas.BODY_AND_HEADER_EVENT.id(), Schemas.BODY_AND_HEADER_EVENT);
        SCHEMA_MAP.put(Schemas.HEADERS_ONLY_EVENT.id(), Schemas.HEADERS_ONLY_EVENT);
        SCHEMA_MAP.put(Schemas.STRING_EVENT.id(), Schemas.STRING_EVENT);
        SCHEMA_MAP.put(Schemas.STRUCTURE_EVENT.id(), Schemas.STRUCTURE_EVENT);
        SCHEMA_MAP.put(Schemas.TEST_EVENT_STREAM.id(), Schemas.TEST_EVENT_STREAM);
        SCHEMA_MAP.put(Schemas.TEST_OPERATION_INPUT.id(), Schemas.TEST_OPERATION_INPUT);
        SCHEMA_MAP.put(Schemas.TEST_OPERATION_OUTPUT.id(), Schemas.TEST_OPERATION_OUTPUT);
    }

    @Override
    public Schema getSchema(ShapeId id) {
        return SCHEMA_MAP.get(id);
    }
}
