/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;


@SmithyGenerated
public final class GetPersonImage implements SdkOperation<GetPersonImageInput, GetPersonImageOutput> {

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .type(ShapeType.OPERATION)
        .id("smithy.example#GetPersonImage")
        .traits(

        )
        .build();

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(GetPersonImageInput.ID, GetPersonImageInput.class, GetPersonImageInput::builder)
        .putType(GetPersonImageOutput.ID, GetPersonImageOutput.class, GetPersonImageOutput::builder)
        .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
        .build();

    @Override
    public SdkShapeBuilder<GetPersonImageInput> inputBuilder() {
        return GetPersonImageInput.builder();
    }

    @Override
    public SdkShapeBuilder<GetPersonImageOutput> outputBuilder() {
        return GetPersonImageOutput.builder();
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public SdkSchema inputSchema() {
        return GetPersonImageInput.SCHEMA;
    }

    @Override
    public SdkSchema outputSchema() {
        return GetPersonImageOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

