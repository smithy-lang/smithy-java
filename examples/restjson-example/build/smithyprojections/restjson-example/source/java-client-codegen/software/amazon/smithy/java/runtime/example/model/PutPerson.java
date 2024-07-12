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
public final class PutPerson implements ApiOperation<PutPersonInput, PutPersonOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPerson");

    static final Schema SCHEMA = Schema.createOperation(ID,
        HttpTrait.builder().method("PUT").code(200).uri(UriPattern.parse("/persons/{name}")).build());

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(PutPersonInput.ID, PutPersonInput.class, PutPersonInput::builder)
        .putType(PutPersonOutput.ID, PutPersonOutput.class, PutPersonOutput::builder)
        .putType(ValidationError.ID, ValidationError.class, ValidationError::builder)
        .build();

    @Override
    public @NonNull ShapeBuilder<PutPersonInput> inputBuilder() {
        return PutPersonInput.builder();
    }

    @Override
    public @NonNull ShapeBuilder<PutPersonOutput> outputBuilder() {
        return PutPersonOutput.builder();
    }

    @Override
    public @NonNull Schema schema() {
        return SCHEMA;
    }

    @Override
    public @NonNull Schema inputSchema() {
        return PutPersonInput.SCHEMA;
    }

    @Override
    public @NonNull Schema outputSchema() {
        return PutPersonOutput.SCHEMA;
    }

    @Override
    public @NonNull TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

