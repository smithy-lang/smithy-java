

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BlobMembers implements ApiOperation<BlobMembersInput, BlobMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BlobMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(BlobMembersInput.ID, BlobMembersInput.class, BlobMembersInput::builder)
        .putType(BlobMembersOutput.ID, BlobMembersOutput.class, BlobMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<BlobMembersInput> inputBuilder() {
        return BlobMembersInput.builder();
    }

    @Override
    public ShapeBuilder<BlobMembersOutput> outputBuilder() {
        return BlobMembersOutput.builder();
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
        return BlobMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

