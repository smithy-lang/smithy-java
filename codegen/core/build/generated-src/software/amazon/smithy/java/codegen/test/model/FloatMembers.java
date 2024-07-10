

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class FloatMembers implements ApiOperation<FloatMembersInput, FloatMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#FloatMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(FloatMembersInput.ID, FloatMembersInput.class, FloatMembersInput::builder)
        .putType(FloatMembersOutput.ID, FloatMembersOutput.class, FloatMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<FloatMembersInput> inputBuilder() {
        return FloatMembersInput.builder();
    }

    @Override
    public ShapeBuilder<FloatMembersOutput> outputBuilder() {
        return FloatMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return FloatMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return FloatMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

