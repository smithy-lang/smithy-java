/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

/**
 * Maps Smithy types to Java Symbols for server code generation.
 */
@SmithyInternalApi
public final class ServiceJavaSymbolProvider extends JavaSymbolProvider {
    private final String serviceName;

    public ServiceJavaSymbolProvider(Model model, ServiceShape service, String packageNamespace, String serviceName) {
        super(model, service, packageNamespace);
        this.serviceName = serviceName;
    }

    @Override
    public Symbol operationShape(OperationShape operationShape) {
        var baseSymbol = super.operationShape(operationShape);
        String stubName = baseSymbol.getName() + "Operation";
        String asyncStubName = stubName + "Async";
        String operationFieldName = StringUtils.uncapitalize(baseSymbol.getName());
        var stubSymbol = Symbol.builder()
            .name(stubName)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .namespace(format("%s.service", packageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", packageNamespace().replace(".", "/"), stubName))
            .build();
        var asyncStubSymbol = Symbol.builder()
            .name(asyncStubName)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .namespace(format("%s.service", packageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", packageNamespace().replace(".", "/"), asyncStubName))
            .build();
        var apiOperationSymbol = super.operationShape(operationShape);
        return baseSymbol.toBuilder()
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .putProperty(ServerSymbolProperties.OPERATION_FIELD_NAME, operationFieldName)
            .putProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION, asyncStubSymbol)
            .putProperty(ServerSymbolProperties.STUB_OPERATION, stubSymbol)
            .putProperty(ServerSymbolProperties.API_OPERATION, apiOperationSymbol)
            .build();
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        return getServerJavaClassSymbol();
    }

    private Symbol getServerJavaClassSymbol() {
        return Symbol.builder()
            .name(serviceName)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .namespace(format("%s.service", packageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", packageNamespace().replace(".", "/"), serviceName))
            .build();
    }
}
