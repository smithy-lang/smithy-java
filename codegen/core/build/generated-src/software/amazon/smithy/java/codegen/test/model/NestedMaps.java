

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NestedMaps implements ApiOperation<NestedMapsInput, NestedMapsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.maps#NestedMaps");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(NestedMapsInput.ID, NestedMapsInput.class, NestedMapsInput::builder)
        .putType(NestedMapsOutput.ID, NestedMapsOutput.class, NestedMapsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<NestedMapsInput> inputBuilder() {
        return NestedMapsInput.builder();
    }

    @Override
    public ShapeBuilder<NestedMapsOutput> outputBuilder() {
        return NestedMapsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return NestedMapsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return NestedMapsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

