/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.jackson;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.json.JsonSerdeProvider;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.utils.SmithyInternalApi;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultPrettyPrinter;

@SmithyInternalApi
public class JacksonJsonSerdeProvider implements JsonSerdeProvider {

    private static final JsonFactory FACTORY;
    private static final ObjectWriteContext PRETTY_PRINT_CONTEXT;
    static final SerializedStringCache SERIALIZED_STRINGS = new SerializedStringCache();

    static {
        FACTORY = JsonFactory.builder()
                .enable(StreamWriteFeature.USE_FAST_DOUBLE_WRITER)
                .enable(StreamReadFeature.USE_FAST_DOUBLE_PARSER)
                .enable(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER)
                .build();
        PRETTY_PRINT_CONTEXT = new ObjectWriteContext.Base() {
            @Override
            public PrettyPrinter getPrettyPrinter() {
                return new DefaultPrettyPrinter();
            }
        };
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
            JsonSettings settings
    ) {
        try {
            return new JacksonJsonDeserializer(FACTORY.createParser(readCtx(settings), source), settings);
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings) {
        int offset = source.arrayOffset() + source.position();
        int length = source.remaining();
        var ctx = readCtx(settings);
        try {
            return new JacksonJsonDeserializer(FACTORY.createParser(ctx, source.array(), offset, length), settings);
        } catch (JacksonException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeSerializer newSerializer(
            OutputStream sink,
            JsonSettings settings
    ) {
        var ctx = writeCtx(settings);
        return new JacksonJsonSerializer(FACTORY.createGenerator(ctx, sink), settings);
    }

    private static ObjectWriteContext writeCtx(JsonSettings settings) {
        return settings.prettyPrint() ? PRETTY_PRINT_CONTEXT : ObjectWriteContext.empty();
    }

    private static ObjectReadContext readCtx(JsonSettings settings) {
        return ObjectReadContext.empty();
    }
}
