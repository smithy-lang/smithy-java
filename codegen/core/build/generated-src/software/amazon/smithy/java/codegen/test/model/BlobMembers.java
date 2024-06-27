

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.java.runtime.core.schema.Unit;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BlobMembers implements ApiOperation<BlobMembersInput, Unit> {

    static final Schema SCHEMA = Schema.builder()
        .type(ShapeType.OPERATION)
        .id("smithy.java.codegen.test.structures#BlobMembers")
        .build();

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(BlobMembersInput.ID, BlobMembersInput.class, BlobMembersInput::builder)
        .putType(Unit.ID, Unit.class, Unit::builder)
        .build();

    @Override
    public ShapeBuilder<BlobMembersInput> inputBuilder() {
        return BlobMembersInput.builder();
    }

    @Override
    public ShapeBuilder<Unit> outputBuilder() {
        return Unit.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return BlobMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return Unit.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

