

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ListAllTypes implements ApiOperation<ListAllTypesInput, ListAllTypesOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#ListAllTypes");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ListAllTypesInput.ID, ListAllTypesInput.class, ListAllTypesInput::builder)
        .putType(ListAllTypesOutput.ID, ListAllTypesOutput.class, ListAllTypesOutput::builder)
        .build();

    @Override
    public ShapeBuilder<ListAllTypesInput> inputBuilder() {
        return ListAllTypesInput.builder();
    }

    @Override
    public ShapeBuilder<ListAllTypesOutput> outputBuilder() {
        return ListAllTypesOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ListAllTypesInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ListAllTypesOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

