

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SelfReference implements ApiOperation<SelfReferenceInput, SelfReferenceOutput> {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#SelfReference");

    static final Schema SCHEMA = Schema.createOperation(ID);

    private final TypeRegistry typeRegistry = TypeRegistry.builder()
        .putType(SelfReferenceInput.ID, SelfReferenceInput.class, SelfReferenceInput::builder)
        .putType(SelfReferenceOutput.ID, SelfReferenceOutput.class, SelfReferenceOutput::builder)
        .build();

    @Override
    public ShapeBuilder<SelfReferenceInput> inputBuilder() {
        return SelfReferenceInput.builder();
    }

    @Override
    public ShapeBuilder<SelfReferenceOutput> outputBuilder() {
        return SelfReferenceOutput.builder();
    }

    @Override
    public Schema schema() {
        return SCHEMA;
    }

    @Override
    public Schema inputSchema() {
        return SelfReferenceInput.SCHEMA;
    }

    @Override
    public Schema outputSchema() {
        return SelfReferenceOutput.SCHEMA;
    }

    @Override
    public TypeRegistry typeRegistry() {
        return typeRegistry;
    }
}

