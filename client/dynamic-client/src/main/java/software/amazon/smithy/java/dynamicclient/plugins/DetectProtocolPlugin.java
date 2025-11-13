/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.ClientProtocolFactory;
import software.amazon.smithy.java.client.core.ProtocolSettings;
import software.amazon.smithy.java.dynamicclient.settings.ModelSetting;
import software.amazon.smithy.java.dynamicclient.settings.ServiceIdSetting;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.traits.Trait;

/**
 * DynamicClient attempts to detect the protocol to use from the model.
 */
public final class DetectProtocolPlugin implements ClientPlugin {

    public static final DetectProtocolPlugin INSTANCE = new DetectProtocolPlugin();
    private static final List<ClientProtocolFactory<Trait>> PROTOCOL_FACTORIES;

    static {
        List<ClientProtocolFactory<Trait>> factories = new ArrayList<>();
        for (var protocolImpl : ServiceLoader.load(ClientProtocolFactory.class)) {
            factories.add(protocolImpl);
        }
        PROTOCOL_FACTORIES = Collections.unmodifiableList(factories);
    }

    @Override
    public Phase getPluginPhase() {
        return Phase.FIRST;
    }

    // TODO: pick a protocol that matches the transport if transport is set.
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

        for (var protocolImpl : PROTOCOL_FACTORIES) {
            if (protocols.containsKey(protocolImpl.id())) {
                var settings = ProtocolSettings.builder().service(service).build();
                config.protocol(protocolImpl.createProtocol(settings, protocols.get(protocolImpl.id())));
                return;
            }
        }

        throw new IllegalArgumentException(
                "Could not find any matching protocol implementations for the "
                        + "following protocol traits attached to service " + service
                        + ": " + protocols.keySet());
    }
}
