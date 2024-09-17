/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.JsonSerdeProvider;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public class JacksonJsonSerdeProvider implements JsonSerdeProvider {

    private static final JsonFactory FACTORY;
    static final SerializedStringCache SERIALIZED_STRINGS = new SerializedStringCache();

    static {
        var serBuilder = new JsonFactoryBuilder();
        serBuilder.disable(JsonFactory.Feature.INTERN_FIELD_NAMES);
        serBuilder.enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER);
        serBuilder.enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER);
        FACTORY = serBuilder.build();
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getName() {
        return "jackson";
    }

    @Override
    public ShapeDeserializer newDeserializer(
        byte[] source,
        JsonCodec.Settings settings
    ) {
        try {
            return new JacksonJsonDeserializer(FACTORY.createParser(source), settings);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonCodec.Settings settings) {
        try {
            int offset = source.arrayOffset() + source.position();
            int length = source.remaining();
            return new JacksonJsonDeserializer(FACTORY.createParser(source.array(), offset, length), settings);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeSerializer newSerializer(
        OutputStream sink,
        JsonCodec.Settings settings
    ) {
        try {
            return new JacksonJsonSerializer(FACTORY.createGenerator(sink), settings);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }
}
