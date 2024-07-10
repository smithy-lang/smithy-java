

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class IntegerMembers implements ApiOperation<IntegerMembersInput, IntegerMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#IntegerMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(IntegerMembersInput.ID, IntegerMembersInput.class, IntegerMembersInput::builder)
        .putType(IntegerMembersOutput.ID, IntegerMembersOutput.class, IntegerMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<IntegerMembersInput> inputBuilder() {
        return IntegerMembersInput.builder();
    }

    @Override
    public ShapeBuilder<IntegerMembersOutput> outputBuilder() {
        return IntegerMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return IntegerMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return IntegerMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

