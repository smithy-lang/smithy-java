/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

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
    ClientTransport<RequestT, ResponseT> createTransport();

    /**
     * Create a {@link ClientTransport} with a user-provided configuration.
     *
     * <p>Configurations are typically specified in the configuration of the client-codegen plugin.
     */
    ClientTransport<RequestT, ResponseT> createTransport(TransportSettings settings);

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

    static List<ClientTransportFactory<?, ?>> load(ClassLoader classLoader) {
        List<ClientTransportFactory<?, ?>> factories = new ArrayList<>();
        // Add all transport services to a sorted, so they can be quickly queried for a compatible class
        ServiceLoader.load(ClientTransportFactory.class, classLoader).forEach(factories::add);
        factories.sort(Comparator.comparingInt(ClientTransportFactory::priority));
        return factories;
    }
}
