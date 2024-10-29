/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

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

                private Builder() {}

                @Override
                public Schema schema() {
                    return $$SCHEMA;
                }

                ${builderSetters:C|}

                ${buildMethod:C|}

                ${errorCorrection:C|}

                ${deserializer:C|}
            }""";
        writer.putContext("sdkShapeBuilder", ShapeBuilder.class);
        writer.putContext("builderProperties", writer.consumer(this::generateProperties));
        writer.putContext("builderSetters", writer.consumer(this::generateSetters));
        writer.putContext("buildMethod", writer.consumer(this::generateBuild));
        writer.putContext("errorCorrection", writer.consumer(this::generateErrorCorrection));
        writer.putContext("deserializer", writer.consumer(this::generateDeserialization));
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

    protected abstract void generateSetters(JavaWriter writer);

    protected abstract void generateBuild(JavaWriter writer);

    protected void generateStages(JavaWriter writer) {
        // Do not generate stages by default
    }

    protected List<String> stageInterfaces() {
        return List.of();
    }
}
