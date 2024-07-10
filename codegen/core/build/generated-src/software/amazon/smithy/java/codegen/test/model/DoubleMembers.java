

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DoubleMembers implements ApiOperation<DoubleMembersInput, DoubleMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#DoubleMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(DoubleMembersInput.ID, DoubleMembersInput.class, DoubleMembersInput::builder)
        .putType(DoubleMembersOutput.ID, DoubleMembersOutput.class, DoubleMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<DoubleMembersInput> inputBuilder() {
        return DoubleMembersInput.builder();
    }

    @Override
    public ShapeBuilder<DoubleMembersOutput> outputBuilder() {
        return DoubleMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return DoubleMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return DoubleMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

