/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integration;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ClientIntegration implements JavaCodegenIntegration {

    @Override
    public String name() {
        return "client-core";
    }

    @Override
    public SymbolProvider decorateSymbolProvider(
        Model model,
        JavaCodegenSettings settings,
        SymbolProvider symbolProvider
    ) {
        return new SymbolProvider() {
            @Override
            public Symbol toSymbol(Shape shape) {
                if (shape.isServiceShape()) {
                    return getServiceSymbol(settings, shape.asServiceShape().get(), symbolProvider.toSymbol(shape));
                }
                return symbolProvider.toSymbol(shape);
            }

            // Explicitly delegate to ensure initial toMemberName is not squashed by decorating
            @Override
            public String toMemberName(MemberShape shape) {
                return symbolProvider.toMemberName(shape);
            }
        };
    }

    private static Symbol getServiceSymbol(JavaCodegenSettings settings, ServiceShape shape, Symbol symbol) {
        var name = CodegenUtils.getDefaultName(shape, shape);
        return getSymbolFromName(settings, name, false).toBuilder()
            .putProperty(ClientSymbolProperties.ASYNC_SYMBOL, getSymbolFromName(settings, name + "Async", true))
            .build();
    }

    private static Symbol getSymbolFromName(JavaCodegenSettings settings, String name, boolean async) {
        var symbol = Symbol.builder()
            .name(name)
            .putProperty(SymbolProperties.IS_PRIMITIVE, false)
            .putProperty(ClientSymbolProperties.ASYNC, async)
            .namespace(format("%s.client", settings.packageNamespace()), ".")
            .declarationFile(format("./%s/client/%s.java", settings.packageNamespace().replace(".", "/"), name))
            .build();

        return symbol.toBuilder()
            .putProperty(
                ClientSymbolProperties.CLIENT_IMPL,
                symbol.toBuilder()
                    .name(name + "Client")
                    .declarationFile(symbol.getDefinitionFile().replace(name, name + "Client"))
                    .build()
            )
            .build();
    }
}
