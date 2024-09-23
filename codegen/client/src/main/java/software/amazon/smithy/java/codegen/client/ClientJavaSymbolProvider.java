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

    public ClientJavaSymbolProvider(Model model, ServiceShape service, String packageNamespace) {
        super(model, service, packageNamespace);
    }

    @Override
    public Symbol serviceShape(ServiceShape serviceShape) {
        var name = CodegenUtils.getDefaultName(serviceShape, serviceShape);
        return getSymbolFromName(name, false).toBuilder()
            .putProperty(ClientSymbolProperties.ASYNC_SYMBOL, getSymbolFromName(name + "Async", true))
            .build();
    }

    private Symbol getSymbolFromName(String name, boolean async) {
        var symbol = Symbol.builder()
            .name(name + "Client")
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .putProperty(ClientSymbolProperties.ASYNC, async)
            .namespace(format("%s.client", packageNamespace()), ".")
            .definitionFile(format("./%s/client/%sClient.java", packageNamespace().replace(".", "/"), name))
            .build();

        return symbol.toBuilder()
            .putProperty(
                ClientSymbolProperties.CLIENT_IMPL,
                symbol.toBuilder()
                    .name(name + "ClientImpl")
                    .definitionFile(
                        format("./%s/client/%sClientImpl.java", packageNamespace().replace(".", "/"), name)
                    )
                    .build()
            )
            .build();
    }
}
