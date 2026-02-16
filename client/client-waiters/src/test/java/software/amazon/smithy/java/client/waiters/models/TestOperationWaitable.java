/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters.models;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.ApiService;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;

public final class TestOperationWaitable implements ApiOperation<GetFoosInput, GetFoosOutput> {
    private static final Schema SCHEMA = Schema.createOperation(ShapeId.from("foo.bar#operationA"));

    @Override
    public ShapeBuilder<GetFoosInput> inputBuilder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ShapeBuilder<GetFoosOutput> outputBuilder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return GetFoosInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return GetFoosOutput.SCHEMA;
    }

    @Override
    public TypeRegistry errorRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Schema> errorSchemas() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ApiService service() {
        return new WaiterApiService();
    }
}
