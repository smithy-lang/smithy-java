

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class DocumentMembers implements ApiOperation<DocumentMembersInput, DocumentMembersOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#DocumentMembers");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(DocumentMembersInput.ID, DocumentMembersInput.class, DocumentMembersInput::builder)
        .putType(DocumentMembersOutput.ID, DocumentMembersOutput.class, DocumentMembersOutput::builder)
        .build();

    @Override
    public ShapeBuilder<DocumentMembersInput> inputBuilder() {
        return DocumentMembersInput.builder();
    }

    @Override
    public ShapeBuilder<DocumentMembersOutput> outputBuilder() {
        return DocumentMembersOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return DocumentMembersInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return DocumentMembersOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

