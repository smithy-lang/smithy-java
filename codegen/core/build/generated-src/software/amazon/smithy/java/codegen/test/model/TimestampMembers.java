

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class TimestampMembers implements ApiOperation<TimestampMembersInput, TimestampMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#TimestampMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(TimestampMembersInput.ID, TimestampMembersInput.class, TimestampMembersInput::builder)
        .putType(TimestampMembersOutput.ID, TimestampMembersOutput.class, TimestampMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<TimestampMembersInput> inputBuilder() {
        return TimestampMembersInput.builder();
    }

    @Override
    public ShapeBuilder<TimestampMembersOutput> outputBuilder() {
        return TimestampMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return TimestampMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return TimestampMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

