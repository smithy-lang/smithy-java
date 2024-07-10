

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class Defaults implements ApiOperation<DefaultsInput, DefaultsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#Defaults");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(DefaultsInput.ID, DefaultsInput.class, DefaultsInput::builder)
        .putType(DefaultsOutput.ID, DefaultsOutput.class, DefaultsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<DefaultsInput> inputBuilder() {
        return DefaultsInput.builder();
    }

    @Override
    public ShapeBuilder<DefaultsOutput> outputBuilder() {
        return DefaultsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return DefaultsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return DefaultsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

