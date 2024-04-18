/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.utils.SmithyGenerated;


@SmithyGenerated
public final class PutPersonImage implements SdkOperation<PutPersonImageInput, PutPersonImageOutput> {

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .type(ShapeType.OPERATION)
        .id("smithy.example#PutPersonImage")
        .traits(
            new HttpTrait.Provider().createTrait(
                ShapeId.from("smithy.api#http"),
                Node.objectNodeBuilder()
                    .withMember("method", "PUT")
                    .withMember("uri", "/persons/{name}/images")
                    .withMember("code", 200)
                    .build()
            )
        )
        .build();

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

