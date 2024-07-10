

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Compile-only checks that naming collisions are handled correctly and
 * generate valid code.
 */
@SmithyGenerated
public final class Naming implements ApiOperation<NamingInput, NamingOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.naming#Naming");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(NamingInput.ID, NamingInput.class, NamingInput::builder)
        .putType(NamingOutput.ID, NamingOutput.class, NamingOutput::builder)
        .build();

    @Override
    public ShapeBuilder<NamingInput> inputBuilder() {
        return NamingInput.builder();
    }

    @Override
    public ShapeBuilder<NamingOutput> outputBuilder() {
        return NamingOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return NamingInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return NamingOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

