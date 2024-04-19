/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;

public final class GetPersonImage implements SdkOperation<GetPersonImageInput, GetPersonImageOutput> {

    private static final Schema SCHEMA = Schema.builder()
        .id(ShapeId.from("smithy.example#GetPerson"))
        .type(ShapeType.OPERATION)
        .traits(
            HttpTrait.builder()
                .method("GET")
                .uri(UriPattern.parse("/anything"))// ("/persons/{name}"))
                .code(200)
                .build()
        )
        .build();

    // Each operation maintains a type registry of the input, output, and errors it can throw.
    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(GetPersonImageInput.ID, GetPersonImageInput.class, GetPersonImageInput::builder)
        .putType(GetPersonImageOutput.ID, GetPersonImageOutput.class, GetPersonImageOutput::builder)
        .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
        .build();

    @Override
    public ShapeBuilder<GetPersonImageInput> inputBuilder() {
        return GetPersonImageInput.builder();
    }

    @Override
    public ShapeBuilder<GetPersonImageOutput> outputBuilder() {
        return GetPersonImageOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return GetPersonImageInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return GetPersonImageOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}
