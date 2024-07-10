

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class UnionMembers implements ApiOperation<UnionMembersInput, UnionMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#UnionMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(UnionMembersInput.ID, UnionMembersInput.class, UnionMembersInput::builder)
        .putType(UnionMembersOutput.ID, UnionMembersOutput.class, UnionMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<UnionMembersInput> inputBuilder() {
        return UnionMembersInput.builder();
    }

    @Override
    public ShapeBuilder<UnionMembersOutput> outputBuilder() {
        return UnionMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return UnionMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return UnionMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

