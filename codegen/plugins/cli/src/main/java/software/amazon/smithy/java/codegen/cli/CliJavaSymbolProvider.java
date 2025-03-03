/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Maps Smithy types to Java Symbols for Client code generation.
 */
final class CliJavaSymbolProvider extends JavaSymbolProvider {
    private final String serviceName;

    public CliJavaSymbolProvider(Model model, ServiceShape service, String packageNamespace, String serviceName) {
        super(model, service, packageNamespace);
        this.serviceName = serviceName;
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        return Symbol.builder()
                .name(serviceName + "Command")
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .namespace(packageNamespace(), ".")
                .definitionFile(format("./%s/%sCommand.java", packageNamespace().replace(".", "/"), serviceName))
                .build();
    }
}
