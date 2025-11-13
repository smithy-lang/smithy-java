/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.plugins;

import java.util.List;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientTransportFactory;

/**
 * Attempts to discover a client transport for the configured protocol.
 */
public final class DiscoverTransportPlugin implements ClientPlugin {

    public static final DiscoverTransportPlugin INSTANCE = new DiscoverTransportPlugin();

    private static final List<ClientTransportFactory<?, ?>> TRANSPORT_FACTORIES = ClientTransportFactory
            .load(ClientConfig.class.getClassLoader());

    private DiscoverTransportPlugin() {}

    @Override
    public Phase getPluginPhase() {
        return Phase.FIRST;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (config.transport() != null || config.protocol() == null) {
            return;
        }

        var protocol = config.protocol();
        for (var factory : TRANSPORT_FACTORIES) {
            // Find the first applicable transport factory
            if (factory.messageExchange().equals(protocol.messageExchange())) {
                config.transport(factory.createTransport());
                return;
            }
        }
    }
}
