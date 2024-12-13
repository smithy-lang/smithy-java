/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination.models;

import java.util.List;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.PaginatedTrait;

public final class TestOperationPaginated implements ApiOperation<GetFoosInput, GetFoosOutput> {
    private static final Schema SCHEMA = Schema.createOperation(
        ShapeId.from("foo.bar#operationA"),
        PaginatedTrait.builder()
            .inputToken("nextToken")
            .outputToken("result.nextToken")
            .items("result.foos")
            .pageSize("maxResults")
            .build()
    );

    @Override
    public ShapeBuilder<GetFoosInput> inputBuilder() {
        return GetFoosInput.builder();
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
    public TypeRegistry typeRegistry() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<ShapeId> effectiveAuthSchemes() {
        throw new UnsupportedOperationException();
    }
}
