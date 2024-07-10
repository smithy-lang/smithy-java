

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Checks that client error correction is correctly performed
 * see: https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction
 */
@SmithyGenerated
public final class ClientErrorCorrection implements ApiOperation<ClientErrorCorrectionInput, ClientErrorCorrectionOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ClientErrorCorrection");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(ClientErrorCorrectionInput.ID, ClientErrorCorrectionInput.class, ClientErrorCorrectionInput::builder)
        .putType(ClientErrorCorrectionOutput.ID, ClientErrorCorrectionOutput.class, ClientErrorCorrectionOutput::builder)
        .build();

    @Override
    public ShapeBuilder<ClientErrorCorrectionInput> inputBuilder() {
        return ClientErrorCorrectionInput.builder();
    }

    @Override
    public ShapeBuilder<ClientErrorCorrectionOutput> outputBuilder() {
        return ClientErrorCorrectionOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return ClientErrorCorrectionInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return ClientErrorCorrectionOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

