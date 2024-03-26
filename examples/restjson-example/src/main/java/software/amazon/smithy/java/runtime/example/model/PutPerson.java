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

public final class PutPerson implements SdkOperation<PutPersonInput, PutPersonOutput> {

    private static final SdkSchema SCHEMA = SdkSchema.builder()
            .id(ShapeId.from("smithy.example#PutPerson"))
            .type(ShapeType.OPERATION)
            .traits(HttpTrait.builder().method("PUT").uri(UriPattern.parse("/persons/{name}")).code(200).build())
            .build();

    // Each operation maintains a type registry of the input, output, and errors it can throw.
    private final TypeRegistry typeRegistry = TypeRegistry.builder()
            .putType(PutPersonInput.ID, PutPersonInput.class, PutPersonInput::builder)
            .putType(PutPersonOutput.ID, PutPersonOutput.class, PutPersonOutput::builder)
            .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
            .build();

    @Override
    public SdkShapeBuilder<PutPersonInput> inputBuilder() {
        return PutPersonInput.builder();
    }

    @Override
    public SdkShapeBuilder<PutPersonOutput> outputBuilder() {
        return PutPersonOutput.builder();
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public SdkSchema inputSchema() {
        return PutPersonInput.SCHEMA;
    }

    @Override
    public SdkSchema outputSchema() {
        return PutPersonOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}
