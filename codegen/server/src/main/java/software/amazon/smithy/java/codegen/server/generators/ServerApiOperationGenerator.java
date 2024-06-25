/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server.generators;

import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.generators.OperationGenerator;
import software.amazon.smithy.java.codegen.server.ServerSymbolProperties;

public class ServerApiOperationGenerator extends OperationGenerator {

    @Override
    public void accept(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        super.generate(directive, directive.symbol().expectProperty(ServerSymbolProperties.API_OPERATION_SYMBOL));
    }
}
