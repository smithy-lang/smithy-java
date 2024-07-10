

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BigIntegerMembers implements ApiOperation<BigIntegerMembersInput, BigIntegerMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BigIntegerMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(BigIntegerMembersInput.ID, BigIntegerMembersInput.class, BigIntegerMembersInput::builder)
        .putType(BigIntegerMembersOutput.ID, BigIntegerMembersOutput.class, BigIntegerMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<BigIntegerMembersInput> inputBuilder() {
        return BigIntegerMembersInput.builder();
    }

    @Override
    public ShapeBuilder<BigIntegerMembersOutput> outputBuilder() {
        return BigIntegerMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return BigIntegerMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return BigIntegerMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

