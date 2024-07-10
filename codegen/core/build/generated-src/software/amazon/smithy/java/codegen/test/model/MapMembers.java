

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class MapMembers implements ApiOperation<MapMembersInput, MapMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#MapMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(MapMembersInput.ID, MapMembersInput.class, MapMembersInput::builder)
        .putType(MapMembersOutput.ID, MapMembersOutput.class, MapMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<MapMembersInput> inputBuilder() {
        return MapMembersInput.builder();
    }

    @Override
    public ShapeBuilder<MapMembersOutput> outputBuilder() {
        return MapMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return MapMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return MapMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

