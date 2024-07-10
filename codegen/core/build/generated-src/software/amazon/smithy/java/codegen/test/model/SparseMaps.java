

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SparseMaps implements ApiOperation<SparseMapsInput, SparseMapsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.maps#SparseMaps");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(SparseMapsInput.ID, SparseMapsInput.class, SparseMapsInput::builder)
        .putType(SparseMapsOutput.ID, SparseMapsOutput.class, SparseMapsOutput::builder)
        .build();

    @Override
    public ShapeBuilder<SparseMapsInput> inputBuilder() {
        return SparseMapsInput.builder();
    }

    @Override
    public ShapeBuilder<SparseMapsOutput> outputBuilder() {
        return SparseMapsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return SparseMapsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return SparseMapsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

