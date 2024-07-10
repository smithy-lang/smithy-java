

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ShortMembers implements ApiOperation<ShortMembersInput, ShortMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ShortMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ShortMembersInput.ID, ShortMembersInput.class, ShortMembersInput::builder)
        .putType(ShortMembersOutput.ID, ShortMembersOutput.class, ShortMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<ShortMembersInput> inputBuilder() {
        return ShortMembersInput.builder();
    }

    @Override
    public ShapeBuilder<ShortMembersOutput> outputBuilder() {
        return ShortMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ShortMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ShortMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

