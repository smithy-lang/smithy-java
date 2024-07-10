

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ByteMembers implements ApiOperation<ByteMembersInput, ByteMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ByteMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ByteMembersInput.ID, ByteMembersInput.class, ByteMembersInput::builder)
        .putType(ByteMembersOutput.ID, ByteMembersOutput.class, ByteMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<ByteMembersInput> inputBuilder() {
        return ByteMembersInput.builder();
    }

    @Override
    public ShapeBuilder<ByteMembersOutput> outputBuilder() {
        return ByteMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ByteMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ByteMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

