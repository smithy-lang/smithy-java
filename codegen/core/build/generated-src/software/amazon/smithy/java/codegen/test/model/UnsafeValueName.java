

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
public final class UnsafeValueName implements ApiOperation<UnsafeValueNameInput, UnsafeValueNameOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#UnsafeValueName");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(UnsafeValueNameInput.ID, UnsafeValueNameInput.class, UnsafeValueNameInput::builder)
        .putType(UnsafeValueNameOutput.ID, UnsafeValueNameOutput.class, UnsafeValueNameOutput::builder)
        .build();

    @Override
    public ShapeBuilder<UnsafeValueNameInput> inputBuilder() {
        return UnsafeValueNameInput.builder();
    }

    @Override
    public ShapeBuilder<UnsafeValueNameOutput> outputBuilder() {
        return UnsafeValueNameOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return UnsafeValueNameInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return UnsafeValueNameOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

