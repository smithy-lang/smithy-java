/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import static java.lang.String.format;

import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;

final class WaiterCodegenUtils {
    /**
     * Determine the symbol to use for the Waiter container
     * @param clientSymbol Symbol for the client this waiter container is being created for.
     * @param settings Code generation settings
     * @param async whether this container will contain asynchronous waiters.
     * @return Symbol representing waiter container class.
     */
    static Symbol getWaiterSymbol(Symbol clientSymbol, JavaCodegenSettings settings, boolean async) {
        var baseClientName = clientSymbol.getName().substring(0, clientSymbol.getName().lastIndexOf("Client"));
        var waiterName = async ? baseClientName + "AsyncWaiter" : baseClientName + "Waiter";
        return Symbol.builder()
                .name(waiterName)
                .namespace(format("%s.client", settings.packageNamespace()), ".")
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .definitionFile(format("./%s/client/%s.java",
                        settings.packageNamespace().replace(".", "/"),
                        waiterName))
                .build();
    }
}
