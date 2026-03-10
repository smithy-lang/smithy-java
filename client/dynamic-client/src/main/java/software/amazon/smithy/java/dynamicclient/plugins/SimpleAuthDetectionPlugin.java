/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import software.amazon.smithy.java.client.core.AutoClientPlugin;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.dynamicclient.settings.ModelSetting;
import software.amazon.smithy.java.dynamicclient.settings.ServiceIdSetting;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A plugin used to detect if built-in auth schemes can be applied to a client automatically.
 */
@SuppressWarnings("rawtypes")
public final class SimpleAuthDetectionPlugin implements AutoClientPlugin {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(SimpleAuthDetectionPlugin.class);

    private static final Map<ShapeId, AuthSchemeFactory> AUTH_SCHEME_FACTORIES = new HashMap<>();
    static {
        for (var factory : ServiceLoader.load(AuthSchemeFactory.class,
                SimpleAuthDetectionPlugin.class.getClassLoader())) {
            AUTH_SCHEME_FACTORIES.put(factory.schemeId(), factory);
        }
    }

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

    @SuppressWarnings("unchecked")
    private void injectAuthSchemeResolver(ClientConfig.Builder config, Model model, ShapeId service) {
        var index = ServiceIndex.of(model);
        var potentialAuthSchemes = index.getEffectiveAuthSchemes(service);
        if (potentialAuthSchemes.isEmpty()) {
            config.authSchemeResolver(AuthSchemeResolver.NO_AUTH);
            return;
        }

        // Make a set of the auth schemes explicitly configured since we don't want to overwrite them.
        var existingSchemeIds = new HashSet<>(config.supportedAuthSchemes().size());
        for (var scheme : config.supportedAuthSchemes()) {
            existingSchemeIds.add(scheme.schemeId());
        }

        for (var entry : potentialAuthSchemes.entrySet()) {
            var id = entry.getKey();
            if (existingSchemeIds.contains(id)) {
                continue;
            }
            var factory = AUTH_SCHEME_FACTORIES.get(id);
            if (factory != null) {
                config.putSupportedAuthSchemes(factory.createAuthScheme(entry.getValue()));
            } else {
                LOGGER.warn("Could not find software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory "
                        + "implementation for auth scheme {}", id);
            }
        }

        config.authSchemeResolver(AuthSchemeResolver.DEFAULT);
    }
}
