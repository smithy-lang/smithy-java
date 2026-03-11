/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.core.error.ErrorFault;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ServiceExceptionGenerator<
        T extends ShapeDirective<ServiceShape, CodeGenerationContext, JavaCodegenSettings>>
        implements Consumer<T> {

    @Override
    public void accept(T directive) {
        var shape = directive.shape();
        var delegator = directive.context().writerDelegator();
        var serviceSymbol = directive.symbolProvider().toSymbol(shape);
        var errorSymbol = serviceSymbol.expectProperty(SymbolProperties.SERVICE_EXCEPTION);

        var syntheticErrorShape = StructureShape.builder()
                .id(errorSymbol.getNamespace() + "#" + errorSymbol.getName())
                .addTrait(new DocumentationTrait(
                        """
                                Base-level exception for the service.

                                <p>Some exceptions do not extend from this class, including synthetic, implicit, and shared exception types."""))
                .addTrait(new ErrorTrait("server"))
                .build();

        delegator.useFileWriter(errorSymbol.getDeclarationFile(), errorSymbol.getNamespace(), writer -> {
            writer.pushState(new ClassSection(syntheticErrorShape));
            var template =
                    """
                            public abstract class ${serviceException:L} extends ${modeledException:T} {
                                protected ${serviceException:L}(
                                        ${schema:T} schema,
                                        String message,
                                        Throwable cause,
                                        ${errorFault:T} errorType,
                                        Boolean captureStackTrace,
                                        boolean deserialized
                                ) {
                                    super(schema, message, cause, errorType, captureStackTrace, deserialized);
                                }
                            }""";
            writer.putContext("serviceException", errorSymbol.getName());
            writer.putContext("modeledException", ModeledException.class);
            writer.putContext("schema", Schema.class);
            writer.putContext("errorFault", ErrorFault.class);
            writer.write(template);
            writer.popState();
        });
    }
}
