/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SchemaFieldGenerator extends ShapeVisitor.Default<Void> implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final CodeGenerationContext context;
    private final ContextualDirective<CodeGenerationContext, JavaCodegenSettings> directive;
    private final SchemaFieldOrder schemaFieldOrder;

    public SchemaFieldGenerator(
            ContextualDirective<CodeGenerationContext, JavaCodegenSettings> directive,
            JavaWriter writer,
            Shape shape
    ) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = directive.symbolProvider();
        this.model = directive.model();
        this.context = directive.context();
        this.directive = directive;
        this.schemaFieldOrder = context.schemaFieldOrder();
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("schemaClass", Schema.class);
        writer.putContext("id", shape.getId());
        writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        writer.pushState();
        writer.putContext("name", schemaFieldOrder.getSchemaFieldName(shape, writer));
        writer.write("static final ${schemaClass:T} $$SCHEMA = ${name:L};");
        writer.popState();
        return null;
    }

    @Override
    public Void structureShape(StructureShape shape) {
        writer.pushState();
        writer.putContext("name", schemaFieldOrder.getSchemaFieldName(shape, writer));
        writer.write("public static final ${schemaClass:T} $$SCHEMA = ${name:L};");

        for (var member : shape.members()) {
            writeMemberProperty(member);
        }

        writer.popState();
        return null;
    }

    @Override
    public Void unionShape(UnionShape shape) {
        writer.pushState();
        writer.putContext("name", schemaFieldOrder.getSchemaFieldName(shape, writer));
        writer.write("public static final ${schemaClass:T} $$SCHEMA = ${name:L};");

        for (var member : shape.members()) {
            writeMemberProperty(member);
        }

        writer.popState();
        return null;
    }

    @Override
    public Void operationShape(OperationShape shape) {
        writer.pushState();
        writer.putContext("name", "$SCHEMA");
        writer.write(
                "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createOperation(${shapeId:T}.from(${id:S})${traits:C});");
        writer.popState();
        return null;
    }

    @Override
    public Void resourceShape(ResourceShape resourceShape) {
        writer.pushState();
        writer.putContext("name", "$SCHEMA");
        writer.write(
                "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createResource(${shapeId:T}.from(${id:S})${traits:C});");
        writer.popState();
        return null;
    }

    @Override
    public Void serviceShape(ServiceShape serviceShape) {
        writer.pushState();
        writer.putContext("name", "$SCHEMA");
        writer.write(
                "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createService(${shapeId:T}.from(${id:S})${traits:C});");
        writer.popState();
        return null;
    }

    @Override
    public Void enumShape(EnumShape shape) {
        writer.pushState();
        writer.putContext("name", "$SCHEMA");
        writer.putContext("variants", shape.members().stream().map(symbolProvider::toMemberName).toList());
        writer.putContext("set", Set.class);
        writer.write("""
                public static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createEnum(${shapeId:T}.from(${id:S}),
                    ${set:T}.of(${#variants}${value:L}.value${^key.last}, ${/key.last}${/variants})${traits:C}
                );
                """);
        writer.popState();
        return null;
    }

    @Override
    public Void intEnumShape(IntEnumShape shape) {
        writer.pushState();
        writer.putContext("name", "$SCHEMA");
        writer.putContext("variants", shape.members().stream().map(symbolProvider::toMemberName).toList());
        writer.putContext("set", Set.class);
        writer.write(
                """
                        public static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createIntEnum(${shapeId:T}.from(${id:S}),
                            ${set:T}.of(${#variants}${value:L}.value${^key.last}, ${/key.last}${/variants})${traits:C}
                        );
                        """);
        writer.popState();
        return null;
    }

    private void writeMemberProperty(MemberShape member) {
        writer.pushState();
        writer.putContext("memberName", member.getMemberName());
        writer.putContext("memberSchema", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));
        writer.write("private static final ${schemaClass:T} ${memberSchema:L} = $$SCHEMA.member(${memberName:S});");
        writer.popState();
    }
}
