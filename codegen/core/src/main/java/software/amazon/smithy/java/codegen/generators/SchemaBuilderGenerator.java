/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class SchemaBuilderGenerator extends ShapeVisitor.Default<Void> implements Runnable {
    private final JavaWriter writer;
    private final Shape shape;
    private final Model model;
    private final CodeGenerationContext context;

    SchemaBuilderGenerator(
        JavaWriter writer,
        Shape shape,
        Model model,
        CodeGenerationContext context
    ) {
        this.writer = writer;
        this.shape = shape;
        this.model = model;
        this.context = context;
    }

    @Override
    public void run() {
        if (!CodegenUtils.recursiveShape(model, shape)) {
            // Only recursive shapes need builders
            return;
        }
        writer.pushState();
        writer.putContext("schemaClass", Schema.class);
        writer.putContext("id", shape.toShapeId());
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("schemaBuilder", SchemaBuilder.class);
        writer.putContext("name", CodegenUtils.toSchemaName(shape));
        writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        // Non-collections do not generate a builder and should never be recursive.
        throw new IllegalStateException("Tried to create schema builder for invalid shape: " + shape);
    }

    @Override
    public Void listShape(ListShape shape) {
        writer.write(
            "static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.listBuilder(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void mapShape(MapShape shape) {
        writer.write(
            "static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.mapBuilder(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void structureShape(StructureShape shape) {
        writer.write(
            "static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.structureBuilder(ID${traits:C});"
        );
        return null;
    }

    @Override
    public Void unionShape(UnionShape shape) {
        writer.write(
            "static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.structureBuilder(ID${traits:C});"
        );
        return null;
    }
}
