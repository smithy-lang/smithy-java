

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NestedLists implements ApiOperation<NestedListsInput, NestedListsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#NestedLists");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(NestedListsInput.ID, NestedListsInput.class, NestedListsInput::builder)
        .putType(NestedListsOutput.ID, NestedListsOutput.class, NestedListsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<NestedListsInput> inputBuilder() {
        return NestedListsInput.builder();
    }

    @Override
    public ShapeBuilder<NestedListsOutput> outputBuilder() {
        return NestedListsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return NestedListsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return NestedListsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

