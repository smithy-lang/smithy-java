

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SparseLists implements ApiOperation<SparseListsInput, SparseListsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#SparseLists");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(SparseListsInput.ID, SparseListsInput.class, SparseListsInput::builder)
        .putType(SparseListsOutput.ID, SparseListsOutput.class, SparseListsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<SparseListsInput> inputBuilder() {
        return SparseListsInput.builder();
    }

    @Override
    public ShapeBuilder<SparseListsOutput> outputBuilder() {
        return SparseListsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return SparseListsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return SparseListsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

