/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Creates a {@link ClientTransport}.
 *
 * <p>This interface is used to discover and create transports within a client builder and in dynamic clients.
 */
public interface ClientTransportFactory<RequestT, ResponseT> {
    /**
     * The name of the transport created by this factory.
     *
     * <p>The transport's name is used to select the default transport in the client settings.
     * No two transports may have the same name.
     *
     * @return the name of the transport
     */
    String name();

    /**
     * Get the message exchange.
     *
     * @return the message exchange.
     */
    MessageExchange<RequestT, ResponseT> messageExchange();

    /**
     * Priority used to select when deciding between multiple transport options.
     *
     * <p>Higher numbers come before lower numbers.
     *
     * @return the priority order.
     */
    default byte priority() {
        return 0;
    }

    /**
     * Create a {@link ClientTransport} with a default configuration.
     *
     * <p>Transports must be able to be instantiated without any arguments for use in dynamic clients.
     */
    default ClientTransport<RequestT, ResponseT> createTransport() {
        return createTransport(Document.EMPTY_MAP, Document.EMPTY_MAP);
    }

    /**
     * Create a {@link ClientTransport} with transport-specific settings and the full plugin settings.
     *
     * <p>The {@code pluginSettings} document contains the full codegen plugin configuration, allowing
     * transports to read shared settings (e.g., {@code httpConfig}) that are not transport-specific.
     * Transport-specific settings should take precedence over shared settings.
     */
    ClientTransport<RequestT, ResponseT> createTransport(Document settings, Document pluginSettings);

    /**
     * Loads all {@link ClientTransportFactory} implementations and sorts them by priority.
     *
     * @param classLoader {@link ClassLoader} to use for loading service implementations
     * @return list of discovered {@link ClientTransportFactory} implementations, sorted by priority
     */
    static List<ClientTransportFactory<?, ?>> load(ClassLoader classLoader) {
        List<ClientTransportFactory<?, ?>> factories = new ArrayList<>();
        for (var service : ServiceLoader.load(ClientTransportFactory.class, classLoader)) {
            factories.add(service);
        }
        factories.sort(Comparator.comparingInt(ClientTransportFactory::priority));
        return factories;
    }
}
