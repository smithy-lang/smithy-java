/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.io.OutputStream;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Handles JSON serialization and deserialization.
 * <p>
 * Support for using {@link JsonNameTrait} can be enabled using {@link Builder#useJsonName(boolean)}.
 * The default timestamp format of epoch-seconds can be changed using
 * {@link Builder#defaultTimestampFormat(TimestampFormatter)}.
 * <p>
 * Support for respecting the {@link TimestampFormatTrait}
 * can be enabled using {@link Builder#useTimestampFormat(boolean)}.
 */
public final class JsonCodec implements Codec {

    private final boolean useJsonName;
    private final boolean useTimestampFormat;
    private final TimestampFormatter defaultTimestampFormat;

    private JsonCodec(Builder builder) {
        this.useJsonName = builder.useJsonName;
        this.useTimestampFormat = builder.useTimestampFormat;
        this.defaultTimestampFormat = builder.defaultTimestampFormat;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String getMediaType() {
        return "application/json";
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return new JsonSerializer(sink, useJsonName, defaultTimestampFormat, useTimestampFormat);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return new JsonDeserializer(source, useJsonName, defaultTimestampFormat, useTimestampFormat);
    }

    public static final class Builder {
        private boolean useJsonName;
        private boolean useTimestampFormat = false;
        private TimestampFormatter defaultTimestampFormat = TimestampFormatter.Prelude.EPOCH_SECONDS;

        private Builder() {
        }

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
            this.defaultTimestampFormat = defaultTimestampFormat;
            return this;
        }
    }
}
