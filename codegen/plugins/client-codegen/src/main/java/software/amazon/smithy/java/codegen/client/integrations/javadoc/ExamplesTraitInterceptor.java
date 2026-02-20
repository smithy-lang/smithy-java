/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.javadoc;

import java.nio.file.Paths;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.generators.SnippetGenerator;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.ExamplesTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.StringUtils;

/**
 * Adds Javadoc examples to operations with the examples trait.
 */
public class ExamplesTraitInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    private final CodeGenerationContext context;

    public ExamplesTraitInterceptor(CodeGenerationContext context) {
        this.context = context;
    }

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        var operation = section.targetedShape()
                .asOperationShape()
                .orElseThrow(() -> new CodegenException(String.format(
                        "Expected shape to be an operation shape, but was " + section.targetedShape().getType())));
        var trait = section.targetedShape().expectTrait(ExamplesTrait.class);
        writer.pushState();

        var operationSymbol = context.symbolProvider().toSymbol(operation);

        // The effective heading levels are different if the documentation is being put
        // in the operation's generated class docs or the client's generated method docs,
        // so this checks to see which file we're in and adjusts the heading level
        // accordingly.
        var operationFile = Paths.get(operationSymbol.getDefinitionFile()).normalize();
        var activeFile = Paths.get(writer.getFilename()).normalize();
        if (operationFile.equals(activeFile)) {
            writer.putContext("sectionHeading", "h2");
            writer.putContext("titleHeading", "h3");
        } else {
            writer.putContext("sectionHeading", "h4");
            writer.putContext("titleHeading", "h5");
        }

        writer.write("<${sectionHeading:L}>Examples</${sectionHeading:L}>");
        for (ExamplesTrait.Example example : trait.getExamples()) {
            writer.pushState();
            writer.putContext("docs", example.getDocumentation().orElse(null));
            writer.putContext("title", example.getTitle());
            writer.write("""
                    <${titleHeading:L}>${title:L}</${titleHeading:L}>

                    ${?docs}<p>${docs:L}
                    ${/docs}
                    {@snippet :
                    ${C|}
                    }
                    """, writer.consumer(w -> writeExampleSnippet(writer, operation, example)));
            writer.popState();
        }
        writer.popState();
    }

    // TODO: collect these and write them out to a shared snippets file
    private void writeExampleSnippet(JavaWriter writer, OperationShape operation, ExamplesTrait.Example example) {
        var service = context.model().expectShape(context.settings().service(), ServiceShape.class);
        var operationName = StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service));
        writer.putContext("operationName", operationName);

        var inputShape = context.model().expectShape(operation.getInputShape());
        writer.putContext(
                "input",
                SnippetGenerator.generateShapeInitializer(context, inputShape, example.getInput()));

        if (example.getOutput().isPresent() && !example.getOutput().get().isEmpty()) {
            var outputShape = context.model().expectShape(operation.getOutputShape());
            writer.putContext(
                    "output",
                    SnippetGenerator.generateShapeInitializer(context, outputShape, example.getOutput().get()));
        } else {
            writer.putContext("output", null);
        }

        if (example.getError().isPresent()) {
            writer.putContext("hasError", true);
            var error = example.getError().get();
            var errorShape = context.model().expectShape(error.getShapeId());
            writer.putContext(
                    "error",
                    SnippetGenerator.generateShapeInitializer(context, errorShape, error.getContent()));
            writer.putContext("errorSymbol", context.symbolProvider().toSymbol(errorShape));
        } else {
            writer.putContext("hasError", false);
        }

        writer.writeInline("""
                var input = ${input:L|};
                ${?hasError}

                try {
                    client.${operationName:L}(input);
                } catch (${errorSymbol:T} e) {
                    e.equals(${error:L|});
                }
                ${/hasError}
                ${^hasError}

                var result = client.${operationName:L}(input);
                result.equals(${output:L|});
                ${/hasError}""");
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        // The examples trait can only be applied to operations for now,
        // but we add an explicit check anyway in case it ever gets expanded.
        var shape = section.targetedShape();
        return shape.hasTrait(ExamplesTrait.class) && shape.isOperationShape();
    }
}
