/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import software.amazon.smithy.java.core.serde.document.Document;

public final class ConfigProviders {
    private static final Map<String, ConfigProviderFactory> PROVIDERS;

    static {
        Map<String, ConfigProviderFactory> providers = new HashMap<>();
        for (var provider : ServiceLoader.load(ConfigProviderFactory.class)) {
            providers.put(provider.identifier(), provider);
        }
        PROVIDERS = Collections.unmodifiableMap(providers);
    }

    private final Map<String, ConfigProviderFactory> providers;

    public ConfigProviders(Builder builder) {
        this.providers = builder.providers;
    }

    public ConfigProvider getProvider(String identifier, Document input) {
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
        private final Map<String, ConfigProviderFactory> providers = new HashMap<>(PROVIDERS);

        private Builder() {

        }

        public Builder addProvider(ConfigProviderFactory provider) {
            providers.put(provider.identifier(), provider);
            return this;
        }

        public ConfigProviders build() {
            return new ConfigProviders(this);
        }

    }
}
