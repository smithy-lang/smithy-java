/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaUtils;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Generates a static nested {@code Builder} class for a Java class.
 */
abstract class BuilderGenerator implements Runnable {
    private final JavaWriter writer;
    protected final Shape shape;
    protected final SymbolProvider symbolProvider;
    protected final Model model;
    protected final ServiceShape service;

    protected BuilderGenerator(
        JavaWriter writer,
        Shape shape,
        SymbolProvider symbolProvider,
        Model model,
        ServiceShape service
    ) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
        this.service = service;
    }

    @Override
    public void run() {
        writer.pushState();
        var template = """
            ${?isStaged}
            ${stageGen:C|}

            ${/isStaged}
            /**
             * @return returns a new Builder.
             */
            public static Builder builder() {
                return new Builder();
            }

            /**
             * Builder for {@link ${shape:T}}.
             */
            public static final class Builder implements ${sdkShapeBuilder:T}<${shape:T}>${?isStaged}, ${#stages}${value:L}${^key.last}, ${/key.last}${/stages}${/isStaged} {
                ${builderProperties:C|}

                ${builderConstructor:C|}

                @Override
                public ${schema:T} schema() {
                    return $$SCHEMA;
                }

                ${builderSetters:C|}

                ${buildMethod:C|}

                ${setMemberValue:C|}

                ${errorCorrection:C|}

                ${deserializer:C|}
            }""";
        writer.putContext("schema", Schema.class);
        writer.putContext("sdkShapeBuilder", ShapeBuilder.class);
        writer.putContext("builderProperties", writer.consumer(this::generateProperties));
        writer.putContext("builderConstructor", writer.consumer(this::generateConstructor));
        writer.putContext("builderSetters", writer.consumer(this::generateSetters));
        writer.putContext("buildMethod", writer.consumer(this::generateBuild));
        writer.putContext("errorCorrection", writer.consumer(this::generateErrorCorrection));
        writer.putContext("deserializer", writer.consumer(this::generateDeserialization));
        writer.putContext("setMemberValue", writer.consumer(this::generateSetMemberValue));
        boolean isStaged = !this.stageInterfaces().isEmpty();
        writer.putContext("isStaged", isStaged);
        if (isStaged) {
            writer.putContext("stages", this.stageInterfaces());
            writer.putContext("stageGen", writer.consumer(this::generateStages));
        }
        writer.write(template);
        writer.popState();
    }

    /**
     * Generates an error correction implementation for shapes with required, non-default members.
     *
     * @see <a href="https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction">client error correction</a>
     */
    protected void generateErrorCorrection(JavaWriter writer) {
        // Do not generate error correction by default
    }

    protected void generateDeserialization(JavaWriter writer) {
        writer.writeInline("${C|}", new StructureDeserializerGenerator(writer, shape, symbolProvider, model, service));
    }

    protected abstract void generateProperties(JavaWriter writer);

    protected void generateConstructor(JavaWriter writer) {
        writer.write("private Builder() {}");
    }

    protected abstract void generateSetters(JavaWriter writer);

    protected abstract void generateBuild(JavaWriter writer);

    protected void generateStages(JavaWriter writer) {
        // Do not generate stages by default
    }

    protected List<String> stageInterfaces() {
        return List.of();
    }

    protected void generateSetMemberValue(JavaWriter writer) {
        // Don't override the default implementation that throws if there are no members.
        if (shape.members().isEmpty() || (shape.getType() == ShapeType.ENUM || shape.getType() == ShapeType.INT_ENUM)) {
            return;
        }

        var template = """
            @Override
            public void setMemberValue(Schema member, Object value) {
                switch (member.memberIndex()) {
                    ${memberSetters:C|}
                    default -> ${shapeBuilderClass:T}.super.setMemberValue(member, value);
                }
            }""";
        writer.putContext("memberSetters", writer.consumer(this::generateMemberValueSetters));
        writer.putContext("shapeBuilderClass", ShapeBuilder.class);
        writer.write(template);
    }

    protected void generateMemberValueSetters(JavaWriter writer) {
        int idx = 0;
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.putContext("type", symbolProvider.toSymbol(member));
            writer.putContext("memberSchema", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));
            writer.putContext("schemaUtilsClass", SchemaUtils.class);
            writer.write(
                "case $L -> ${memberName:L}((${type:T}) ${schemaUtilsClass:T}.validateSameMember(${memberSchema:L}, member, value));",
                idx
            );
            writer.popState();
        }
    }
}
