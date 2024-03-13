/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.myservice.model;

import software.amazon.smithy.java.runtime.shapes.IOShape;
import software.amazon.smithy.java.runtime.shapes.SdkOperation;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.java.runtime.shapes.TypeRegistry;
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
    public IOShape.Builder<PutPersonImageInput> inputBuilder() {
        return PutPersonImageInput.builder();
    }

    @Override
    public IOShape.Builder<PutPersonImageOutput> outputBuilder() {
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
