

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class MapAllTypes implements ApiOperation<MapAllTypesInput, MapAllTypesOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.maps#MapAllTypes");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(MapAllTypesInput.ID, MapAllTypesInput.class, MapAllTypesInput::builder)
        .putType(MapAllTypesOutput.ID, MapAllTypesOutput.class, MapAllTypesOutput::builder)
        .build();

    @Override
    public ShapeBuilder<MapAllTypesInput> inputBuilder() {
        return MapAllTypesInput.builder();
    }

    @Override
    public ShapeBuilder<MapAllTypesOutput> outputBuilder() {
        return MapAllTypesOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return MapAllTypesInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return MapAllTypesOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

