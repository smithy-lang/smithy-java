

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class IntEnumMembers implements ApiOperation<IntEnumMembersInput, IntEnumMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#IntEnumMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(IntEnumMembersInput.ID, IntEnumMembersInput.class, IntEnumMembersInput::builder)
        .putType(IntEnumMembersOutput.ID, IntEnumMembersOutput.class, IntEnumMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<IntEnumMembersInput> inputBuilder() {
        return IntEnumMembersInput.builder();
    }

    @Override
    public ShapeBuilder<IntEnumMembersOutput> outputBuilder() {
        return IntEnumMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return IntEnumMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return IntEnumMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

