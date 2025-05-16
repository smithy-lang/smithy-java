/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Handles JSON serialization and deserialization.
 *
 * <p>Support for using {@link JsonNameTrait} can be enabled using {@link Builder#useJsonName(boolean)}.
 *
 * <p>Support for respecting the {@link TimestampFormatTrait} can be enabled using
 * {@link Builder#useTimestampFormat(boolean)}. The default timestamp format of epoch-seconds can be changed using
 * {@link Builder#defaultTimestampFormat(TimestampFormatter)}.
 *
 * <p>Blobs are base64 encoded as strings.
 */
public final class JsonCodec implements Codec {

    private final JsonSettings settings;

    private JsonCodec(Builder builder) {
        this.settings = builder.settingsBuilder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public JsonSettings settings() {
        return settings;
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
        private final JsonSettings.Builder settingsBuilder = JsonSettings.builder();

        private Builder() {}

        public JsonCodec build() {
            return new JsonCodec(this);
        }

        /**
         * Use the given settings object with this codec.
         *
         * @param settings Settings to use.
         * @return the builder.
         */
        public Builder settings(JsonSettings settings) {
            settings.updateBuilder(settingsBuilder);
            return this;
        }

        /**
         * Whether to use the jsonName trait or just the member name.
         *
         * <p>The jsonName trait is ignored by default.
         *
         * @param useJsonName True to use the jsonName trait.
         * @return the builder.
         */
        public Builder useJsonName(boolean useJsonName) {
            settingsBuilder.useJsonName(useJsonName);
            return this;
        }

        /**
         * Whether to use the timestampFormat trait or ignore it.
         *
         * <p>The timestampFormat trait is ignored by default.
         *
         * @param useTimestampFormat True to honor the timestampFormat trait.
         * @return the builder.
         */
        public Builder useTimestampFormat(boolean useTimestampFormat) {
            settingsBuilder.useTimestampFormat(useTimestampFormat);
            return this;
        }

        /**
         * The default timestamp format to assume for timestamp values.
         *
         * <p>Assumes "epoch-seconds" by default.
         *
         * @param defaultTimestampFormat The default timestamp format to assume.
         * @return the builder.
         */
        public Builder defaultTimestampFormat(TimestampFormatter defaultTimestampFormat) {
            settingsBuilder.defaultTimestampFormat(defaultTimestampFormat);
            return this;
        }

        /**
         * Whether to forbid or ignore unknown union members.
         *
         * <p>Unknown union members are ignored by default.
         *
         * @param forbid True to forbid unknown union members.
         * @return the builder.
         */
        public Builder forbidUnknownUnionMembers(boolean forbid) {
            settingsBuilder.forbidUnknownUnionMembers(forbid);
            return this;
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
            settingsBuilder.defaultNamespace(defaultNamespace);
            return this;
        }

        /**
         * Whether the type field should be written when Documents are being serialized. Default is true.
         *
         * @param serializeTypeInDocuments if the type field should be written when Documents are being serialized
         * @return the builder
         */
        public Builder serializeTypeInDocuments(boolean serializeTypeInDocuments) {
            settingsBuilder.serializeTypeInDocuments(serializeTypeInDocuments);
            return this;
        }
        
        /**
         * Whether to format the JSON output with pretty printing (indentation and line breaks).
         * Default is false.
         *
         * @param prettyPrint true to enable pretty printing
         * @return the builder
         */
        public Builder prettyPrint(boolean prettyPrint) {
            settingsBuilder.prettyPrint(prettyPrint);
            return this;
        }

        /**
         * Uses a custom JSON serde provider.
         *
         * @param provider the JSON serde provider to use.
         * @return the builder.
         */
        Builder overrideSerdeProvider(JsonSerdeProvider provider) {
            settingsBuilder.overrideSerdeProvider(provider);
            return this;
        }
    }
}
