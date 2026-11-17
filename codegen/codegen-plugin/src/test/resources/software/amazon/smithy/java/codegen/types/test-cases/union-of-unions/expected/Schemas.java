package software.amazon.smithy.java.example.standalone.model;

import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaBuilder;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Defines schemas for shapes in the model package.
 */
final class Schemas {
    static final SchemaBuilder $0_BUILDER = Schema.unionBuilder(ShapeId.from("smithy.test#DeepestUnion"));
    static final SchemaBuilder $1_BUILDER = Schema.unionBuilder(ShapeId.from("smithy.test#InnerUnion"));
    static final SchemaBuilder $2_BUILDER = Schema.unionBuilder(ShapeId.from("smithy.test#OuterUnion"));

    static {
            $0_BUILDER
                .putMember("outer", Schemas.$2_BUILDER)
                .putMember("text", PreludeSchemas.STRING)
                .builderSupplier(DeepestUnion::builder).shapeClass(DeepestUnion.class).build();

            $1_BUILDER
                .putMember("deepest", Schemas.$0_BUILDER)
                .putMember("number", PreludeSchemas.INTEGER)
                .builderSupplier(InnerUnion::builder).shapeClass(InnerUnion.class).build();

            $2_BUILDER
                .putMember("inner", Schemas.$1_BUILDER)
                .putMember("other", PreludeSchemas.STRING)
                .builderSupplier(OuterUnion::builder).shapeClass(OuterUnion.class).build();

    }

    static final Schema $0 = $0_BUILDER.build().resolve();
    static final Schema $1 = $1_BUILDER.build().resolve();
    static final Schema $2 = $2_BUILDER.build().resolve();

    static final Schema[] $ALL = {$0, $1, $2};

    private Schemas() {}
}
