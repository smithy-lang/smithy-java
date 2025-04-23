/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public final class Rpcv2CborCodec implements Codec {
    private final CborSettings settings;

    private Rpcv2CborCodec(Builder builder) {
        this.settings = builder.settings == null ? CborSettings.defaultSettings() : builder.settings.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return settings.provider().newSerializer(sink, settings);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return settings.provider().newDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        return settings.provider().newDeserializer(source, settings);
    }

    public static final class Builder {
        private CborSettings.Builder settings;

        private Builder() {

        }

        private CborSettings.Builder settings() {
            if (settings == null) {
                settings = CborSettings.builder();
            }
            return settings;
        }

        /**
         * Sets the default namespace when attempting to deserialize documents that use a relative shape ID.
         *
         * <p>No default namespace is used unless one is explicitly provided.
         *
         * @param defaultNamespace Default namespace to set.
         * @return the builder.
         */
        public Builder defaultNamespace(String defaultNamespace) {
            settings().defaultNamespace(defaultNamespace);
            return this;
        }

        /**
         * Uses a custom CBOR serde provider.
         *
         * @param provider the CBOR serde provider to use.
         * @return the builder.
         */
        public Builder overrideSerdeProvider(CborSerdeProvider provider) {
            settings().overrideSerdeProvider(provider);
            return this;
        }

        /**
         * Use the given settings object with this codec.
         *
         * @param settings Settings to use.
         * @return the builder.
         */
        public Builder settings(CborSettings settings) {
            settings().updateBuilder(settings);
            return this;
        }

        public Rpcv2CborCodec build() {
            return new Rpcv2CborCodec(this);
        }
    }
}
