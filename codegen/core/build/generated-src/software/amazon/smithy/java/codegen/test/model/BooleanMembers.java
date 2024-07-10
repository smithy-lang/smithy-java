

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BooleanMembers implements ApiOperation<BooleanMembersInput, BooleanMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BooleanMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(BooleanMembersInput.ID, BooleanMembersInput.class, BooleanMembersInput::builder)
        .putType(BooleanMembersOutput.ID, BooleanMembersOutput.class, BooleanMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<BooleanMembersInput> inputBuilder() {
        return BooleanMembersInput.builder();
    }

    @Override
    public ShapeBuilder<BooleanMembersOutput> outputBuilder() {
        return BooleanMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return BooleanMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return BooleanMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

