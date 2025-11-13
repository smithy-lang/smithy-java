/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.plugins;

import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.dynamicclient.settings.ModelSetting;
import software.amazon.smithy.java.dynamicclient.settings.ServiceIdSetting;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A plugin used to detect if built-in auth schemes can be applied to a client automatically.
 */
public final class SimpleAuthDetectionPlugin implements AutoClientPlugin {

    public static final SimpleAuthDetectionPlugin INSTANCE = new SimpleAuthDetectionPlugin();

    @Override
    public Phase getPluginPhase() {
        return Phase.DEFAULTS;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        // Only apply if no auth scheme resolver is set, a model is set, and a service is set (i.e., DynamicClient).
        if (config.authSchemeResolver() == null) {
            var model = config.context().get(ModelSetting.MODEL);
            if (model != null) {
                var service = config.context().get(ServiceIdSetting.SERVICE_ID);
                if (service != null) {
                    injectAuthSchemeResolver(config, model, service);
                }
            }
        }
    }

    private void injectAuthSchemeResolver(ClientConfig.Builder config, Model model, ShapeId service) {
        var index = ServiceIndex.of(model);
        var potentialAuthSchemes = index.getEffectiveAuthSchemes(service);
        if (potentialAuthSchemes.isEmpty()) {
            config.authSchemeResolver(AuthSchemeResolver.NO_AUTH);
        } else {
            // TODO: Add similar behavior as done in ClientInterfaceGenerator to register AuthSchemeFactories.
            config.authSchemeResolver(AuthSchemeResolver.DEFAULT);
        }
    }
}
