/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenFeature;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public final class Rpcv2CborCodec implements Codec {
    private final CborSettings settings;
    private final boolean runtimeCodegenEnabled;
    private final SmithyGeneratedCborSerde generated;

    private Rpcv2CborCodec(Builder builder) {
        this.settings = builder.settings == null ? CborSettings.defaultSettings() : builder.settings.build();
        boolean explicitlyRequested =
                Boolean.TRUE.equals(builder.runtimeCodegen) && RuntimeCodegenFeature.available();
        boolean propertyRequested =
                builder.runtimeCodegen == null && RuntimeCodegenFeature.enabled("cbor");
        if (explicitlyRequested && !(settings.provider() instanceof DefaultCborSerdeProvider)) {
            throw new IllegalStateException(
                    "CBOR runtime code generation decorates only the built-in CBOR provider; "
                            + "remove the custom provider to enable runtime code generation");
        }
        if (propertyRequested
                && !builder.providerOverridden
                && !(settings.provider() instanceof DefaultCborSerdeProvider)) {
            throw new IllegalStateException(
                    "CBOR runtime code generation decorates only the built-in CBOR provider; "
                            + "remove the custom provider or disable the runtime-codegen property");
        }
        this.runtimeCodegenEnabled = explicitlyRequested
                || (propertyRequested && settings.provider() instanceof DefaultCborSerdeProvider);
        this.generated = runtimeCodegenEnabled ? new SmithyGeneratedCborSerde() : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape) {
        if (runtimeCodegenEnabled && shape instanceof SerializableStruct struct) {
            ByteBuffer result = generated.serialize(struct, settings);
            if (result != null) {
                return result;
            }
        }
        return settings.provider().serialize(shape, settings);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(byte[] source, ShapeBuilder<T> builder) {
        if (runtimeCodegenEnabled) {
            T result = generated.deserialize(source, builder, settings);
            if (result != null) {
                return result;
            }
        }
        return Codec.super.deserializeShape(source, builder);
    }

    @Override
    public <T extends SerializableShape> T deserializeShape(ByteBuffer source, ShapeBuilder<T> builder) {
        if (runtimeCodegenEnabled) {
            T result = generated.deserialize(source, builder, settings);
            if (result != null) {
                return result;
            }
        }
        return Codec.super.deserializeShape(source, builder);
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
        private Boolean runtimeCodegen;
        private boolean providerOverridden;

        private Builder() {

        }

        private CborSettings.Builder settings() {
            if (settings == null) {
                settings = CborSettings.builder();
            }
            return settings;
        }

        /**
         * Sets the namespace used to resolve relative shape IDs.
         *
         * @param defaultNamespace namespace to use
         * @return this builder
         */
        public Builder defaultNamespace(String defaultNamespace) {
            settings().defaultNamespace(defaultNamespace);
            return this;
        }

        /**
         * Sets the CBOR serde provider.
         *
         * @param provider provider to use
         * @return this builder
         */
        public Builder overrideSerdeProvider(CborSerdeProvider provider) {
            settings().overrideSerdeProvider(provider);
            providerOverridden = true;
            return this;
        }

        /**
         * Sets the codec settings.
         *
         * @param settings settings to use
         * @return this builder
         */
        public Builder settings(CborSettings settings) {
            settings().updateBuilder(settings);
            providerOverridden = true;
            return this;
        }

        /**
         * Enables runtime-generated codecs on supported JDKs.
         *
         * @param runtimeCodegen whether to enable runtime code generation
         * @return this builder
         */
        public Builder runtimeCodegen(boolean runtimeCodegen) {
            this.runtimeCodegen = runtimeCodegen;
            return this;
        }

        public Rpcv2CborCodec build() {
            return new Rpcv2CborCodec(this);
        }
    }
}
