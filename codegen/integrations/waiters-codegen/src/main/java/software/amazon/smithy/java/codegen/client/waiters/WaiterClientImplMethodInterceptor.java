/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.sections.ClientImplAdditionalMethodsSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

record WaiterClientImplMethodInterceptor(SymbolProvider symbolProvider, JavaCodegenSettings settings)
        implements CodeInterceptor.Prepender<ClientImplAdditionalMethodsSection, JavaWriter> {
    @Override
    public Class<ClientImplAdditionalMethodsSection> sectionType() {
        return ClientImplAdditionalMethodsSection.class;
    }

    @Override
    public void prepend(JavaWriter writer, ClientImplAdditionalMethodsSection section) {
        // TODO: Support Async waiters
        if (section.async()) {
            return;
        }
        var clientSymbol = symbolProvider.toSymbol(section.client());
        writer.pushState();
        writer.putContext("container", WaiterCodegenUtils.getWaiterSymbol(clientSymbol, settings, section.async()));
        writer.write("""
                @Override
                public ${container:T} waiter() {
                    return new ${container:T}(this);
                }
                """);
        writer.popState();
    }
}
