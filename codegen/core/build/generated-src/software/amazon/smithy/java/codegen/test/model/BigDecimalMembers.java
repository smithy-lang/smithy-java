

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BigDecimalMembers implements ApiOperation<BigDecimalMembersInput, BigDecimalMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BigDecimalMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(BigDecimalMembersInput.ID, BigDecimalMembersInput.class, BigDecimalMembersInput::builder)
        .putType(BigDecimalMembersOutput.ID, BigDecimalMembersOutput.class, BigDecimalMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<BigDecimalMembersInput> inputBuilder() {
        return BigDecimalMembersInput.builder();
    }

    @Override
    public ShapeBuilder<BigDecimalMembersOutput> outputBuilder() {
        return BigDecimalMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return BigDecimalMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return BigDecimalMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

