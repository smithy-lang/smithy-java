/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.Objects;
import java.util.ServiceLoader;

public final class CborSettings {
    private static final CborSerdeProvider PROVIDER;

    static {
        final String preferredName = System.getProperty("smithy-java.cbor-provider");
        CborSerdeProvider selected = null;
        for (var provider : ServiceLoader.load(CborSerdeProvider.class, CborSerdeProvider.class.getClassLoader())) {
            if (preferredName != null) {
                if (provider.getName().equals(preferredName)) {
                    selected = provider;
                    break;
                }
            }
            if (selected == null) {
                selected = provider;
            } else if (provider.getPriority() > selected.getPriority()) {
                selected = provider;
            }
        }
        if (selected == null) {
            selected = new DefaultCborSerdeProvider();
        }
        PROVIDER = selected;
    }

    private static final CborSettings DEFAULT = builder().build();

    public static CborSettings defaultSettings() {
        return DEFAULT;
    }

    private final String defaultNamespace;
    private final CborSerdeProvider provider;

    private CborSettings(Builder builder) {
        this.defaultNamespace = builder.defaultNamespace;
        this.provider = builder.provider;
    }

    public CborSerdeProvider provider() {
        return provider;
    }

    public String defaultNamespace() {
        return defaultNamespace;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String defaultNamespace;
        private CborSerdeProvider provider = PROVIDER;

        /**
         * Sets the default namespace when attempting to deserialize documents that use a relative shape ID.
         *
         * <p>No default namespace is used unless one is explicitly provided.
         *
         * @param defaultNamespace Default namespace to set.
         * @return the builder.
         */
        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        /**
         * Uses a custom CBOR serde provider.
         *
         * @param provider the CBOR serde provider to use.
         * @return the builder.
         */
        Builder overrideSerdeProvider(CborSerdeProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
            return this;
        }

        public Builder updateBuilder(CborSettings settings) {
            overrideSerdeProvider(settings.provider());
            defaultNamespace(settings.defaultNamespace());
            return this;
        }

        public CborSettings build() {
            return new CborSettings(this);
        }
    }
}
