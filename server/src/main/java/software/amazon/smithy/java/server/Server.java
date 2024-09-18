/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.logging.InternalLogger;

public interface Server {

    static ServerBuilder<?> builder() {
        return findBuilder(null);
    }

    static ServerBuilder<?> builder(String serverName) {
        return findBuilder(Objects.requireNonNull(serverName, "Server name can't be null"));
    }

    private static ServerBuilder<?> findBuilder(String name) {
        ServerProvider selected = null;
        InternalLogger logger = InternalLogger.getLogger(Server.class);
        List<ServerProvider> providers = ServiceLoader.load(ServerProvider.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .peek(p -> logger.debug("Discovered server provider {}:{}", p.name(), p.getClass()))
            .toList();
        for (var provider : providers) {
            if (provider.name().equals(name)) {
                selected = provider;
                break;
            } else if (selected == null) {
                selected = provider;
            } else if (provider.priority() > selected.priority()) {
                selected = provider;
            } else if (provider.priority() == selected.priority()) {
                throw new IllegalStateException(
                    String.format(
                        "Found multiple server providers with same priority (%s) , (%s))",
                        provider.name(),
                        selected.name()
                    )
                );
            }
        }
        return Objects.requireNonNull(
            selected,
            "Couldn't find a server provider" + (name != null ? " for " + name : "")
        ).serverBuilder();
    }

    void start();

    CompletableFuture<Void> shutdown();
}
