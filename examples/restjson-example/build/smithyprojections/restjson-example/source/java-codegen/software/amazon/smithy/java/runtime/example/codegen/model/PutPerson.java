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
public final class PutPerson implements SdkOperation<PutPersonInput, PutPersonOutput> {

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .type(ShapeType.OPERATION)
        .id("smithy.example#PutPerson")
        .build();

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

