

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SetMembers implements ApiOperation<SetMembersInput, SetMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#SetMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(SetMembersInput.ID, SetMembersInput.class, SetMembersInput::builder)
        .putType(SetMembersOutput.ID, SetMembersOutput.class, SetMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<SetMembersInput> inputBuilder() {
        return SetMembersInput.builder();
    }

    @Override
    public ShapeBuilder<SetMembersOutput> outputBuilder() {
        return SetMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return SetMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return SetMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

