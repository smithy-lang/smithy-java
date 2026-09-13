/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

/**
 * A {@link Codec} for the Sparrowhawk binary format.
 *
 * <p>Serialization requires schemas whose structure and union members are sorted by wire category and idx
 * (the ordering the core schema builder applies), and is byte-compatible with the reference Sparrowhawk
 * implementation when all members carry the {@code smithy.protocols#idx} trait. Document types are not
 * supported.
 */
public final class SparrowhawkCodec implements Codec {

    private static final SparrowhawkCodec INSTANCE = new SparrowhawkCodec();

    private SparrowhawkCodec() {}

    /**
     * Gets the Sparrowhawk codec.
     *
     * @return the codec instance.
     */
    public static SparrowhawkCodec get() {
        return INSTANCE;
    }

    /**
     * Gets the EXPERIMENTAL backward-writing Sparrowhawk codec. Serialization requires shapes generated
     * with the {@code reverseMemberSerialization} codegen setting (descending member dispatch and
     * reverse-iterating list consumers); map entries are emitted in reversed order, which decodes
     * identically but is not byte-identical to {@link #get()}. Deserialization is shared with the
     * standard codec. See perf-opt/backward-writer.md.
     *
     * @return the backward codec instance.
     */
    public static Codec backward() {
        return BackwardCodec.INSTANCE;
    }

    private static final class BackwardCodec implements Codec {
        static final BackwardCodec INSTANCE = new BackwardCodec();

        @Override
        public ByteBuffer serialize(SerializableShape shape) {
            var serializer = SparrowhawkBackwardSerializer.acquire();
            try {
                shape.serialize(serializer);
                return serializer.finish();
            } finally {
                SparrowhawkBackwardSerializer.release(serializer);
            }
        }

        @Override
        public ShapeSerializer createSerializer(OutputStream sink) {
            throw new UnsupportedOperationException(
                    "The backward Sparrowhawk codec does not support streaming serialization");
        }

        @Override
        public ShapeDeserializer createDeserializer(byte[] source) {
            return new SparrowhawkDeserializer(source);
        }

        @Override
        public ShapeDeserializer createDeserializer(ByteBuffer source) {
            return new SparrowhawkDeserializer(source);
        }
    }

    @Override
    public ByteBuffer serialize(SerializableShape shape) {
        var serializer = SparrowhawkSerializer.acquire();
        try {
            shape.serialize(serializer);
            return serializer.finish();
        } finally {
            SparrowhawkSerializer.release(serializer);
        }
    }

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return new SparrowhawkSerializer(sink);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return new SparrowhawkDeserializer(source);
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        return new SparrowhawkDeserializer(source);
    }
}
