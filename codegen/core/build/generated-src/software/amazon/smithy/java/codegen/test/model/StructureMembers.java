

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class StructureMembers implements ApiOperation<StructureMembersInput, StructureMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#StructureMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(StructureMembersInput.ID, StructureMembersInput.class, StructureMembersInput::builder)
        .putType(StructureMembersOutput.ID, StructureMembersOutput.class, StructureMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<StructureMembersInput> inputBuilder() {
        return StructureMembersInput.builder();
    }

    @Override
    public ShapeBuilder<StructureMembersOutput> outputBuilder() {
        return StructureMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return StructureMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return StructureMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

