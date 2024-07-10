

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class EnumMembers implements ApiOperation<EnumMembersInput, EnumMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#EnumMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(EnumMembersInput.ID, EnumMembersInput.class, EnumMembersInput::builder)
        .putType(EnumMembersOutput.ID, EnumMembersOutput.class, EnumMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<EnumMembersInput> inputBuilder() {
        return EnumMembersInput.builder();
    }

    @Override
    public ShapeBuilder<EnumMembersOutput> outputBuilder() {
        return EnumMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return EnumMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return EnumMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

