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
public final class GetPersonImage implements ApiOperation<GetPersonImageInput, GetPersonImageOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImage");

    static final Schema SCHEMA = Schema.createOperation(ID,
        HttpTrait.builder().method("GET").code(200).uri(UriPattern.parse("/persons/{name}/image")).build());

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(GetPersonImageInput.ID, GetPersonImageInput.class, GetPersonImageInput::builder)
        .putType(GetPersonImageOutput.ID, GetPersonImageOutput.class, GetPersonImageOutput::builder)
        .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
        .build();

    @Override
    public @NonNull ShapeBuilder<GetPersonImageInput> inputBuilder() {
        return GetPersonImageInput.builder();
    }

    @Override
    public @NonNull ShapeBuilder<GetPersonImageOutput> outputBuilder() {
        return GetPersonImageOutput.builder();
    }

    @Override
    public @NonNull Schema schema() {
        return SCHEMA;
    }

    @Override
    public @NonNull Schema inputSchema() {
        return GetPersonImageInput.SCHEMA;
    }

    @Override
    public @NonNull Schema outputSchema() {
        return GetPersonImageOutput.SCHEMA;
    }

    @Override
    public @NonNull TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

