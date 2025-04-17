/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;

public final class PluginProviders {
    private final Map<String, BundleClientPluginProviderFactory> providers;

    private PluginProviders(Builder builder) {
        this.providers = builder.providers;
    }

    public BundleClientPluginProvider getProvider(String identifier, Document input) {
        var provider = providers.get(identifier);
        if (provider == null) {
            throw new NullPointerException("no auth provider named " + identifier);
        }

        return provider.createAuthFactory(input);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final Map<String, BundleClientPluginProviderFactory> BASE_PROVIDERS =
                ServiceLoaderLoader.load(BundleClientPluginProviderFactory.class,
                        BundleClientPluginProviderFactory::identifier);

        private Map<String, BundleClientPluginProviderFactory> providers;

        private Builder() {

        }

        public Builder addProvider(BundleClientPluginProviderFactory provider) {
            if (providers == null) {
                providers = new HashMap<>(BASE_PROVIDERS);
            }
            providers.put(provider.identifier(), provider);
            return this;
        }

        public PluginProviders build() {
            if (providers == null) {
                providers = BASE_PROVIDERS;
            }
            return new PluginProviders(this);
        }

    }
}
