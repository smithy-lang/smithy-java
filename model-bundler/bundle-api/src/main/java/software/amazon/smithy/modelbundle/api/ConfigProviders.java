/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;

public final class ConfigProviders {
    private final Map<String, ConfigProviderFactory> providers;

    private ConfigProviders(Builder builder) {
        this.providers = builder.providers;
    }

    public ConfigProvider<?> getProvider(String identifier, Document input) {
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
        private static final Map<String, ConfigProviderFactory> BASE_PROVIDERS =
                ServiceLoaderLoader.load(ConfigProviderFactory.class, ConfigProviderFactory::identifier);

        private Map<String, ConfigProviderFactory> providers;

        private Builder() {

        }

        public Builder addProvider(ConfigProviderFactory provider) {
            if (providers == null) {
                providers = new HashMap<>(BASE_PROVIDERS);
            }
            providers.put(provider.identifier(), provider);
            return this;
        }

        public ConfigProviders build() {
            if (providers == null) {
                providers = BASE_PROVIDERS;
            }
            return new ConfigProviders(this);
        }

    }
}
