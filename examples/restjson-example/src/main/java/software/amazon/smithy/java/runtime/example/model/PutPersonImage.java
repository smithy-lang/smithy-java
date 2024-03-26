/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;

public final class PutPersonImage implements SdkOperation<PutPersonImageInput, PutPersonImageOutput> {

    private static final SdkSchema SCHEMA = SdkSchema.builder()
            .id(ShapeId.from("smithy.example#PutPersonImage"))
            .type(ShapeType.OPERATION)
            .traits(HttpTrait.builder()
                    .method("PUT")
                    // TODO: implement proper label handling
                    .uri(UriPattern.parse("/persons/{name}/images"))
                    .code(200)
                    .build())
            .build();

    // Each operation maintains a type registry of the input, output, and errors it can throw.
    private final TypeRegistry typeRegistry = TypeRegistry.builder()
            .putType(PutPersonImageInput.ID, PutPersonImageInput.class, PutPersonImageInput::builder)
            .putType(PutPersonImageOutput.ID, PutPersonImageOutput.class, PutPersonImageOutput::builder)
            .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
            .build();

    @Override
    public SdkShapeBuilder<PutPersonImageInput> inputBuilder() {
        return PutPersonImageInput.builder();
    }

    @Override
    public SdkShapeBuilder<PutPersonImageOutput> outputBuilder() {
        return PutPersonImageOutput.builder();
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public SdkSchema inputSchema() {
        return PutPersonImageInput.SCHEMA;
    }

    @Override
    public SdkSchema outputSchema() {
        return PutPersonImageOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}
