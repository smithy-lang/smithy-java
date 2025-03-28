/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates a {@code SharedSerde} utility class that contains all unattached schemas for the model.
 */
@SmithyInternalApi
public final class SharedSerdeGenerator
        implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        directive.context()
                .writerDelegator()
                .useFileWriter(
                        CodegenUtils.getSerdeFileName(directive.settings()),
                        CodegenUtils.getModelNamespace(directive.settings()),
                        writer -> writer.write(
                                """
                                        /**
                                         * Defines shared serialization and deserialization methods for map and list shapes.
                                         */
                                        final class SharedSerde {

                                            ${L@sharedSerde|}

                                            private SharedSerde() {}
                                        }
                                        """,
                                ""));
    }
}
