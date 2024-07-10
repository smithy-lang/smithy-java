

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class RecursiveStructs implements ApiOperation<RecursiveStructsInput, RecursiveStructsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveStructs");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(RecursiveStructsInput.ID, RecursiveStructsInput.class, RecursiveStructsInput::builder)
        .putType(RecursiveStructsOutput.ID, RecursiveStructsOutput.class, RecursiveStructsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<RecursiveStructsInput> inputBuilder() {
        return RecursiveStructsInput.builder();
    }

    @Override
    public ShapeBuilder<RecursiveStructsOutput> outputBuilder() {
        return RecursiveStructsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return RecursiveStructsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return RecursiveStructsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

