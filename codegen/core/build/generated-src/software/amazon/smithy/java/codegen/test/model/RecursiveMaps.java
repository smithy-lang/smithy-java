

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class RecursiveMaps implements ApiOperation<RecursiveMapsInput, RecursiveMapsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveMaps");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(RecursiveMapsInput.ID, RecursiveMapsInput.class, RecursiveMapsInput::builder)
        .putType(RecursiveMapsOutput.ID, RecursiveMapsOutput.class, RecursiveMapsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<RecursiveMapsInput> inputBuilder() {
        return RecursiveMapsInput.builder();
    }

    @Override
    public ShapeBuilder<RecursiveMapsOutput> outputBuilder() {
        return RecursiveMapsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return RecursiveMapsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return RecursiveMapsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

