/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.HashMap;
import java.util.Map;

public final class Bundlers {
    private final Map<String, BundlerFactory> providers;

    private Bundlers(Builder builder) {
        this.providers = builder.providers;
    }

    public Bundler getProvider(String identifier, String... args) {
        var provider = providers.get(identifier);
        if (provider == null) {
            throw new NullPointerException("no bundler named " + identifier);
        }

        return provider.newBundler(args);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private static final Map<String, BundlerFactory> BASE_PROVIDERS =
                ServiceLoaderLoader.load(BundlerFactory.class, BundlerFactory::identifier);

        private Map<String, BundlerFactory> providers;

        private Builder() {

        }

        public Builder addProvider(BundlerFactory provider) {
            if (providers == null) {
                providers = new HashMap<>(BASE_PROVIDERS);
            }
            providers.put(provider.identifier(), provider);
            return this;
        }

        public Bundlers build() {
            if (providers == null) {
                providers = BASE_PROVIDERS;
            }
            return new Bundlers(this);
        }

    }
}
