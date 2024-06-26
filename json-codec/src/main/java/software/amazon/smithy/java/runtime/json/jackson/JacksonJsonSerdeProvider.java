/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.JsonSerdeProvider;

public class JacksonJsonSerdeProvider implements JsonSerdeProvider {

    private static final JsonFactory FACTORY = new ObjectMapper().getFactory();

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
            return new JacksonJsonDeserializer(FACTORY.createParser(new ByteBufferBackedInputStream(source)), settings);
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
