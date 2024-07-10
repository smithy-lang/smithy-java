

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Test only checks for successful compilation
 */
@SmithyGenerated
public final class NonSequentialIntEnum implements ApiOperation<NonSequentialIntEnumInput, NonSequentialIntEnumOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#NonSequentialIntEnum");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(NonSequentialIntEnumInput.ID, NonSequentialIntEnumInput.class, NonSequentialIntEnumInput::builder)
        .putType(NonSequentialIntEnumOutput.ID, NonSequentialIntEnumOutput.class, NonSequentialIntEnumOutput::builder)
        .build();

    @Override
    public ShapeBuilder<NonSequentialIntEnumInput> inputBuilder() {
        return NonSequentialIntEnumInput.builder();
    }

    @Override
    public ShapeBuilder<NonSequentialIntEnumOutput> outputBuilder() {
        return NonSequentialIntEnumOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return NonSequentialIntEnumInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return NonSequentialIntEnumOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

