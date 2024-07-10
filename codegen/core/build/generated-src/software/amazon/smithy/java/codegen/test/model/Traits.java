

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Tests that traits are correctly added to schemas and initialize properly
 * when custom initializers are used.
 */
@SmithyGenerated
public final class Traits implements ApiOperation<TraitsInput, TraitsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.traits#Traits");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(TraitsInput.ID, TraitsInput.class, TraitsInput::builder)
        .putType(TraitsOutput.ID, TraitsOutput.class, TraitsOutput::builder)
        .putType(RetryableError.ID, RetryableError.class, RetryableError::builder)
        .build();

    @Override
    public ShapeBuilder<TraitsInput> inputBuilder() {
        return TraitsInput.builder();
    }

    @Override
    public ShapeBuilder<TraitsOutput> outputBuilder() {
        return TraitsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return TraitsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return TraitsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

