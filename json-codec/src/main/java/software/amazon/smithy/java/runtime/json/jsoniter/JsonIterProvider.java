/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.ByteBufferUtils;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.JsonSerdeProvider;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonIterator;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonIteratorPool;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.output.JsonStream;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.output.JsonStreamPool;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class JsonIterProvider implements JsonSerdeProvider {

    @Override
    public int getPriority() {
        return 5;
    }

    @Override
    public String getName() {
        return "jsoniter";
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, JsonCodec.Settings settings) {
        var iter = JsonIteratorPool.borrowJsonIterator();
        iter.reset(source);
        return new JsonIterDeserializer(iter, settings, this::returnIterator);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, JsonCodec.Settings settings) {
        return newDeserializer(ByteBufferUtils.getBytes(source), settings);
    }

    private void returnIterator(JsonIterator iterator) {
        JsonIteratorPool.returnJsonIterator(iterator);
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, JsonCodec.Settings settings) {
        var stream = JsonStreamPool.borrowJsonStream();
        stream.reset(sink);
        return new JsonIterSerializer(stream, settings, this::returnStream);
    }

    private void returnStream(JsonStream stream) {
        JsonStreamPool.returnJsonStream(stream);
    }
}
