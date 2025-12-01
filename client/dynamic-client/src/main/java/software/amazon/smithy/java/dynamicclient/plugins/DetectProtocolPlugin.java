/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.plugins;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientProtocol;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.dynamicclient.settings.ModelSetting;
import software.amazon.smithy.java.dynamicclient.settings.ServiceIdSetting;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * DynamicClient attempts to detect the protocol to use from the model.
 *
 * <p>If a transport is already manually defined, then this plugin only picks a protocol that matches the transport
 * based on the presence of a protocol trait and a {@link ClientProtocolFactory}.
 */
public final class DetectProtocolPlugin implements ClientPlugin {

    public static final DetectProtocolPlugin INSTANCE = new DetectProtocolPlugin();
    private static final List<ClientProtocolFactory<Trait>> PROTOCOL_FACTORIES = new ArrayList<>();

    static {
        for (var protocolImpl : ServiceLoader.load(ClientProtocolFactory.class,
                ClientProtocolFactory.class.getClassLoader())) {
            PROTOCOL_FACTORIES.add(protocolImpl);
        }
    }

    @Override
    public Phase getPluginPhase() {
        return Phase.FIRST;
    }

    // TODO: we currently just pick the first found protocol. We could add something like SMITHY_PREFERRED_PROTOCOLS
    //  later to influence the picked protocol.
    @Override
    public void configureClient(ClientConfig.Builder config) {
        if (config.protocol() != null) {
            return;
        }

        var model = config.context().get(ModelSetting.MODEL);
        if (model == null) {
            return;
        }

        var service = config.context().get(ServiceIdSetting.SERVICE_ID);
        if (service == null) {
            return;
        }

        ServiceIndex serviceIndex = ServiceIndex.of(model);
        var protocols = serviceIndex.getProtocols(service);

        if (protocols.isEmpty()) {
            throw new IllegalArgumentException(
                    "No protocol() was provided, and no protocol definition traits "
                            + "were found on service " + service);
        }

        var transport = config.transport();
        ClientProtocol<?, ?> protocol = transport != null
                ? findProtocolMatchingTransport(service, protocols, transport)
                : findProtocol(service, protocols);

        if (protocol != null) {
            config.protocol(protocol);
            return;
        }

        throw new IllegalArgumentException(
                "Could not find any matching protocol implementations for the "
                        + "following protocol traits attached to service " + service
                        + ": " + protocols.keySet()
                        + " (transport set?=" + (transport == null ? "no" : transport.getClass().getName()) + ")");
    }

    private static ClientProtocol<?, ?> findProtocol(ShapeId service, Map<ShapeId, Trait> protocols) {
        for (var protocolImpl : PROTOCOL_FACTORIES) {
            if (protocols.containsKey(protocolImpl.id())) {
                return createProtocol(service, protocols, protocolImpl);
            }
        }
        return null;
    }

    private static ClientProtocol<?, ?> createProtocol(
            ShapeId service,
            Map<ShapeId, Trait> protocols,
            ClientProtocolFactory<Trait> protocolImpl
    ) {
        var settings = ProtocolSettings.builder().service(service).build();
        return protocolImpl.createProtocol(settings, protocols.get(protocolImpl.id()));
    }

    private static ClientProtocol<?, ?> findProtocolMatchingTransport(
            ShapeId service,
            Map<ShapeId, Trait> protocols,
            ClientTransport<?, ?> transport
    ) {
        for (var protocolImpl : PROTOCOL_FACTORIES) {
            if (protocols.containsKey(protocolImpl.id())) {
                var protocol = createProtocol(service, protocols, protocolImpl);
                // Only return the protocol if it has the same message exchange as the transport.
                if (protocol.messageExchange().equals(transport.messageExchange())) {
                    return protocol;
                }
            }
        }
        return null;
    }
}
