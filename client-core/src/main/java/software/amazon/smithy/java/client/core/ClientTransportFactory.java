/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Collections;
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
        return createTransport(Document.createStringMap(Collections.emptyMap()));
    }

    /**
     * Create a {@link ClientTransport} with a user-provided configuration.
     *
     * <p>Configurations are typically specified in the configuration of the client-codegen plugin.
     */
    ClientTransport<RequestT, ResponseT> createTransport(Document settings);

    /**
     * The request class used by transport.
     *
     * @return the request class.
     */
    Class<RequestT> requestClass();

    /**
     * The response class used by the transport.
     *
     * @return the response class.
     */
    Class<ResponseT> responseClass();

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
