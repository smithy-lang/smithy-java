

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ListMembers implements ApiOperation<ListMembersInput, ListMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ListMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ListMembersInput.ID, ListMembersInput.class, ListMembersInput::builder)
        .putType(ListMembersOutput.ID, ListMembersOutput.class, ListMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<ListMembersInput> inputBuilder() {
        return ListMembersInput.builder();
    }

    @Override
    public ShapeBuilder<ListMembersOutput> outputBuilder() {
        return ListMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ListMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ListMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

