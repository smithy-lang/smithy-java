

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SetsAllTypes implements ApiOperation<SetsAllTypesInput, SetsAllTypesOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#SetsAllTypes");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(SetsAllTypesInput.ID, SetsAllTypesInput.class, SetsAllTypesInput::builder)
        .putType(SetsAllTypesOutput.ID, SetsAllTypesOutput.class, SetsAllTypesOutput::builder)
        .build();

    @Override
    public ShapeBuilder<SetsAllTypesInput> inputBuilder() {
        return SetsAllTypesInput.builder();
    }

    @Override
    public ShapeBuilder<SetsAllTypesOutput> outputBuilder() {
        return SetsAllTypesOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return SetsAllTypesInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return SetsAllTypesOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

