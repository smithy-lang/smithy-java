/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Maps Smithy types to Java Symbols for Client code generation.
 */
final class ClientJavaSymbolProvider extends JavaSymbolProvider {
    private final String serviceName;

    public ClientJavaSymbolProvider(Model model, ServiceShape service, String packageNamespace, String serviceName) {
        super(model, service, packageNamespace);
        this.serviceName = serviceName;
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        return getSymbolFromName()
                .toBuilder()
                .putProperty(SymbolProperties.SERVICE_EXCEPTION,
                        CodegenUtils.getServiceExceptionSymbol(packageNamespace(), serviceName))
                .putProperty(SymbolProperties.SERVICE_API_SERVICE,
                        CodegenUtils.getServiceApiSymbol(packageNamespace(), serviceName))
                .build();
    }

    private Symbol getSymbolFromName() {
        var name = serviceName + "Client";
        var symbol = Symbol.builder()
                .name(name)
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .namespace(format("%s.client", packageNamespace()), ".")
                .definitionFile(format("./%s/client/%s.java", packageNamespace().replace(".", "/"), name))
                .build();

        return symbol.toBuilder()
                .putProperty(
                        ClientSymbolProperties.CLIENT_IMPL,
                        symbol.toBuilder()
                                .name(name + "Impl")
                                .definitionFile(
                                        format("./%s/client/%sImpl.java", packageNamespace().replace(".", "/"), name))
                                .build())
                .build();
    }
}
