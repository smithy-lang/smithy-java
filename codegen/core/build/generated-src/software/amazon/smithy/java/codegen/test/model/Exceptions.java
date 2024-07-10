

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class Exceptions implements ApiOperation<ExceptionsInput, ExceptionsOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.exceptions#Exceptions");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ExceptionsInput.ID, ExceptionsInput.class, ExceptionsInput::builder)
        .putType(ExceptionsOutput.ID, ExceptionsOutput.class, ExceptionsOutput::builder)
        .putType(ExceptionWithExtraStringException.ID, ExceptionWithExtraStringException.class, ExceptionWithExtraStringException::builder)
        .putType(SimpleException.ID, SimpleException.class, SimpleException::builder)
        .putType(EmptyException.ID, EmptyException.class, EmptyException::builder)
        .build();

    @Override
    public ShapeBuilder<ExceptionsInput> inputBuilder() {
        return ExceptionsInput.builder();
    }

    @Override
    public ShapeBuilder<ExceptionsOutput> outputBuilder() {
        return ExceptionsOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ExceptionsInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ExceptionsOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

