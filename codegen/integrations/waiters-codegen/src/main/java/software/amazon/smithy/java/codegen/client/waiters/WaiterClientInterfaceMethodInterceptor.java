/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.sections.ClientInterfaceAdditionalMethodsSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

record WaiterClientInterfaceMethodInterceptor(SymbolProvider symbolProvider, JavaCodegenSettings settings)
        implements CodeInterceptor.Prepender<ClientInterfaceAdditionalMethodsSection, JavaWriter> {
    @Override
    public Class<ClientInterfaceAdditionalMethodsSection> sectionType() {
        return ClientInterfaceAdditionalMethodsSection.class;
    }

    @Override
    public void prepend(JavaWriter writer, ClientInterfaceAdditionalMethodsSection section) {
        // TODO: Support Async waiters
        if (section.async()) {
            return;
        }
        var clientSymbol = symbolProvider.toSymbol(section.client());
        writer.pushState();
        writer.putContext("container", WaiterCodegenUtils.getWaiterSymbol(clientSymbol, settings, section.async()));
        writer.write("""
                /**
                 * Create a new {@link CoffeeShopWaiter} instance that uses this client for polling.
                 *
                 * @return new {@link ${container:T}} instance.
                 */
                ${container:T} waiter();
                """);
        writer.popState();
    }
}
