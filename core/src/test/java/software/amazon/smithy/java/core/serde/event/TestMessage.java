/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * Simple test frame that wraps raw bytes with a 4-byte length prefix.
 */
public final class TestMessage {

    static class TestFrame implements Frame<byte[]> {
        private final byte[] data;

        public TestFrame(byte[] data) {
            this.data = data;
        }

        @Override
        public byte[] unwrap() {
            return data;
        }
    }

    static class TestFrameEncoder implements FrameEncoder<TestFrame> {
        @Override
        public ByteBuffer encode(TestFrame frame) {
            byte[] payload = frame.unwrap();
            ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length);
            buffer.putInt(payload.length);
            buffer.put(payload);
            buffer.flip();
            return buffer;
        }
    }

    static class TestFrameDecoder implements FrameDecoder<TestFrame> {
        private ByteBuffer pending = ByteBuffer.allocate(0);

        @Override
        public List<TestFrame> decode(ByteBuffer buffer) {
            List<TestFrame> frames = new ArrayList<>();
            ByteBuffer combined = ByteBuffer.allocate(pending.remaining() + buffer.remaining());
            combined.put(pending);
            combined.put(buffer);
            combined.flip();

            while (combined.remaining() >= 4) {
                int length = combined.getInt(combined.position());
                if (combined.remaining() < 4 + length) {
                    break;
                }
                combined.getInt(); // consume length
                byte[] payload = new byte[length];
                combined.get(payload);
                frames.add(new TestFrame(payload));
            }

            pending = ByteBuffer.allocate(combined.remaining());
            pending.put(combined);
            pending.flip();
            return frames;
        }
    }

    static class TestEventDecoder implements EventDecoder<TestFrame> {

        @Override
        public SerializableStruct decode(TestFrame frame) {
            var bytes = frame.unwrap();
            var value = new String(bytes, StandardCharsets.UTF_8);
            return new TestEvent(value);
        }

        @Override
        public SerializableStruct decodeInitialEvent(TestFrame frame, EventStream<?> stream) {
            throw new UnsupportedOperationException();
        }
    }

    static class TestEventEncoder implements EventEncoder<TestFrame> {

        @Override
        public TestFrame encode(SerializableStruct item) {
            var testStruct = (TestEvent) item;
            var bytes = testStruct.value.getBytes(StandardCharsets.UTF_8);
            return new TestFrame(bytes);
        }

        @Override
        public TestFrame encodeFailure(Throwable exception) {
            return null;
        }
    }

    static class TestEvent implements SerializableStruct {

        private final String value;

        public TestEvent(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public Schema schema() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    static class TestEventDecoderFactory implements EventDecoderFactory<TestFrame> {

        @Override
        public EventDecoder<TestFrame> newEventDecoder() {
            return new TestEventDecoder();
        }

        @Override
        public FrameDecoder<TestFrame> newFrameDecoder() {
            return new TestFrameDecoder();
        }
    }

    static class TestEventEncoderFactory implements EventEncoderFactory<TestFrame> {

        @Override
        public EventEncoder<TestFrame> newEventEncoder() {
            return new TestEventEncoder();
        }

        @Override
        public FrameEncoder<TestFrame> newFrameEncoder() {
            return new TestFrameEncoder();
        }

        @Override
        public String contentType() {
            return "text/plain";
        }
    }
}
