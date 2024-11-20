/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class DefaultTransforms {
    private DefaultTransforms() {}

    public static void apply(
        CodegenDirector<JavaWriter, JavaCodegenIntegration, CodeGenerationContext, JavaCodegenSettings> runner,
        JavaCodegenSettings settings
    ) {
        runner.changeStringEnumsToEnumShapes(true);
        runner.flattenPaginationInfoIntoOperations();
        runner.makeIdempotencyTokensClientOptional();
        runner.removeShapesDeprecatedBeforeVersion(settings.relativeVersion());
        runner.removeShapesDeprecatedBeforeDate(settings.relativeDate());
        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();
    }
}
