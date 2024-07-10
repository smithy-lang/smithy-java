

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class StringMembers implements ApiOperation<StringMembersInput, StringMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#StringMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(StringMembersInput.ID, StringMembersInput.class, StringMembersInput::builder)
        .putType(StringMembersOutput.ID, StringMembersOutput.class, StringMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<StringMembersInput> inputBuilder() {
        return StringMembersInput.builder();
    }

    @Override
    public ShapeBuilder<StringMembersOutput> outputBuilder() {
        return StringMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return StringMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return StringMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

