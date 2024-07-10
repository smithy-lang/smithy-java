

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class RecursiveLists implements ApiOperation<RecursiveListsInput, RecursiveListsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveLists");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(RecursiveListsInput.ID, RecursiveListsInput.class, RecursiveListsInput::builder)
        .putType(RecursiveListsOutput.ID, RecursiveListsOutput.class, RecursiveListsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<RecursiveListsInput> inputBuilder() {
        return RecursiveListsInput.builder();
    }

    @Override
    public ShapeBuilder<RecursiveListsOutput> outputBuilder() {
        return RecursiveListsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return RecursiveListsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return RecursiveListsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

