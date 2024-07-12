/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.pattern.UriPattern;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonImage implements ApiOperation<PutPersonImageInput, PutPersonImageOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImage");

    static final Schema SCHEMA = Schema.createOperation(ID,
        HttpTrait.builder().method("PUT").code(200).uri(UriPattern.parse("/persons/{name}/images")).build());

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(PutPersonImageInput.ID, PutPersonImageInput.class, PutPersonImageInput::builder)
        .putType(PutPersonImageOutput.ID, PutPersonImageOutput.class, PutPersonImageOutput::builder)
        .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
        .build();

    @Override
    public @NonNull ShapeBuilder<PutPersonImageInput> inputBuilder() {
        return PutPersonImageInput.builder();
    }

    @Override
    public @NonNull ShapeBuilder<PutPersonImageOutput> outputBuilder() {
        return PutPersonImageOutput.builder();
    }

    @Override
    public @NonNull Schema schema() {
        return SCHEMA;
    }

    @Override
    public @NonNull Schema inputSchema() {
        return PutPersonImageInput.SCHEMA;
    }

    @Override
    public @NonNull Schema outputSchema() {
        return PutPersonImageOutput.SCHEMA;
    }

    @Override
    public @NonNull TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

