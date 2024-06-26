/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.StringUtils;

public class ServiceJavaSymbolProvider extends JavaSymbolProvider {

    public ServiceJavaSymbolProvider(Model model, ServiceShape service, String packageNamespace) {
        super(model, service, packageNamespace);
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
            .namespace(format("%s.service", getPackageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", getPackageNamespace().replace(".", "/"), stubName))
            .build();
        var asyncStubSymbol = Symbol.builder()
            .name(asyncStubName)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .namespace(format("%s.service", getPackageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", getPackageNamespace().replace(".", "/"), asyncStubName))
            .build();

        return baseSymbol.toBuilder()
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .putProperty(ServerSymbolProperties.OPERATION_FIELD_NAME, operationFieldName)
            .putProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION, asyncStubSymbol)
            .putProperty(ServerSymbolProperties.STUB_OPERATION, stubSymbol)
            .build();
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        return getServerJavaClassSymbol(serviceShape);
    }

    private Symbol getServerJavaClassSymbol(Shape shape) {
        String name = CodegenUtils.getDefaultName(shape, getService());
        return Symbol.builder()
            .name(name)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .namespace(format("%s.service", getPackageNamespace()), ".")
            .declarationFile(format("./%s/service/%s.java", getPackageNamespace().replace(".", "/"), name))
            .build();
    }
}
