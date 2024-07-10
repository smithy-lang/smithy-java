

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class LongMembers implements ApiOperation<LongMembersInput, LongMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#LongMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(LongMembersInput.ID, LongMembersInput.class, LongMembersInput::builder)
        .putType(LongMembersOutput.ID, LongMembersOutput.class, LongMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<LongMembersInput> inputBuilder() {
        return LongMembersInput.builder();
    }

    @Override
    public ShapeBuilder<LongMembersOutput> outputBuilder() {
        return LongMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return LongMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return LongMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

