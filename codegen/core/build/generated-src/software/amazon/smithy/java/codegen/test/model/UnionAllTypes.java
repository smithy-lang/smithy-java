

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class UnionAllTypes implements ApiOperation<UnionAllTypesInput, UnionAllTypesOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.unions#UnionAllTypes");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(UnionAllTypesInput.ID, UnionAllTypesInput.class, UnionAllTypesInput::builder)
        .putType(UnionAllTypesOutput.ID, UnionAllTypesOutput.class, UnionAllTypesOutput::builder)
        .build();

    @Override
    public ShapeBuilder<UnionAllTypesInput> inputBuilder() {
        return UnionAllTypesInput.builder();
    }

    @Override
    public ShapeBuilder<UnionAllTypesOutput> outputBuilder() {
        return UnionAllTypesOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return UnionAllTypesInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return UnionAllTypesOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

