/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.ServiceLoader;
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
    private static final JsonSerdeProvider PROVIDER;

    static {
        final String preferredName = System.getProperty("smithy-java.json-provider");
        JsonSerdeProvider selected = null;
        for (JsonSerdeProvider provider : ServiceLoader.load(JsonSerdeProvider.class)) {
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
            throw new IllegalStateException("At least one JSON provider should be registered.");
        }
        PROVIDER = selected;
    }

    private final Settings settings;
    private final JsonSerdeProvider provider;

    private JsonCodec(Builder builder) {
        var timestampResolver = builder.useTimestampFormat
            ? new TimestampResolver.UseTimestampFormatTrait(builder.defaultTimestampFormat)
            : new TimestampResolver.StaticFormat(builder.defaultTimestampFormat);
        var fieldMapper = builder.useJsonName
            ? new JsonFieldMapper.UseJsonNameTrait()
            : JsonFieldMapper.UseMemberName.INSTANCE;
        settings = new Settings(
            timestampResolver,
            fieldMapper,
            builder.forbidUnknownUnionMembers,
            builder.defaultNamespace
        );
        provider = builder.provider == null ? PROVIDER : builder.provider;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return provider.newSerializer(sink, settings);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return provider.newDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        return provider.newDeserializer(source, settings);
    }

    public static final class Builder {
        private boolean useJsonName;
        private boolean useTimestampFormat = false;
        private TimestampFormatter defaultTimestampFormat = TimestampFormatter.Prelude.EPOCH_SECONDS;
        private JsonSerdeProvider provider;
        private boolean forbidUnknownUnionMembers;
        private String defaultNamespace;

        private Builder() {}

        public JsonCodec build() {
            return new JsonCodec(this);
        }

        public Builder useJsonName(boolean useJsonName) {
            this.useJsonName = useJsonName;
            return this;
        }

        public Builder useTimestampFormat(boolean useTimestampFormat) {
            this.useTimestampFormat = useTimestampFormat;
            return this;
        }

        public Builder defaultTimestampFormat(TimestampFormatter defaultTimestampFormat) {
            this.defaultTimestampFormat = Objects.requireNonNull(defaultTimestampFormat);
            return this;
        }

        public Builder forbidUnknownUnionMembers(boolean forbid) {
            this.forbidUnknownUnionMembers = forbid;
            return this;
        }

        public Builder defaultNamespace(String defaultNamespace) {
            this.defaultNamespace = defaultNamespace;
            return this;
        }

        Builder overrideSerdeProvider(JsonSerdeProvider provider) {
            this.provider = Objects.requireNonNull(provider);
            return this;
        }

        @Override
        public String toString() {
            return "JsonCodec.Builder{" +
                "useJsonName=" + useJsonName +
                ", useTimestampFormat=" + useTimestampFormat +
                ", defaultTimestampFormat=" + defaultTimestampFormat +
                ", provider=" + provider.getName() +
                ", defaultNamespace=" + defaultNamespace +
                '}';
        }
    }

    public record Settings(
        TimestampResolver timestampResolver,
        JsonFieldMapper fieldMapper,
        boolean forbidUnknownUnionMembers,
        String defaultNamespace
    ) {}
}
