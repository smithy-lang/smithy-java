/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.JsonIterator;
import com.jsoniter.output.JsonStream;
import java.io.OutputStream;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
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

    private final TimestampResolver timestampResolver;
    private final JsonFieldMapper fieldMapper;

    private JsonCodec(Builder builder) {
        timestampResolver = builder.useTimestampFormat
            ? new TimestampResolver.UseTimestampFormatTrait(builder.defaultTimestampFormat)
            : new TimestampResolver.StaticFormat(builder.defaultTimestampFormat);
        fieldMapper = builder.useJsonName
            ? new JsonFieldMapper.UseJsonNameTrait()
            : JsonFieldMapper.UseMemberName.INSTANCE;
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
        var stream = createStream(sink);
        return new JsonSerializer(stream, fieldMapper, timestampResolver, this::returnStream);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return new JsonDeserializer(createIterator(source), timestampResolver, fieldMapper, this::returnIterator);
    }

    // TODO: Implement virtual thread friendly pooling (JsonIter's pooling is done using ThreadLocals).
    private JsonStream createStream(OutputStream sink) {
        return new JsonStream(sink, 1024);
    }

    private JsonIterator createIterator(byte[] source) {
        return JsonIterator.parse(source);
    }

    private void returnStream(JsonStream stream) {
        // TODO: Implement pooling.
    }

    private void returnIterator(JsonIterator iterator) {
        // TODO: Implement pooling.
    }

    public static final class Builder {
        private boolean useJsonName;
        private boolean useTimestampFormat = false;
        private TimestampFormatter defaultTimestampFormat = TimestampFormatter.Prelude.EPOCH_SECONDS;

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
    }
}
