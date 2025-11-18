/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.util.NullOutputStream;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(1)
public class JsonBench {
    JsonCodec codec;
    ByteBuffer buff;
    Schema mainSchema;
    SerializableStruct struct;
    ShapeSerializer serializer;

    @Setup
    public void setup() throws Exception {
        buff = ByteBuffer.allocate(4096);
        codec = JsonCodec.builder()
                .defaultNamespace("foo")
                .useTimestampFormat(true)
                .build();
        var tsId = PreludeSchemas.TIMESTAMP.id();
        mainSchema = Schema.structureBuilder(ShapeId.from("foo#Bar"))
                .putMember("a", Schema.createTimestamp(tsId))
                .putMember("b", Schema.createTimestamp(tsId, new TimestampFormatTrait("epoch-seconds")))
                .putMember("c", Schema.createTimestamp(tsId))
                .putMember("d", Schema.createTimestamp(tsId))
                .putMember("e", Schema.createTimestamp(tsId))
                .putMember("f", Schema.createTimestamp(tsId))
                .putMember("g", Schema.createTimestamp(tsId))
                .putMember("h", Schema.createTimestamp(tsId))
                .putMember("i", Schema.createTimestamp(tsId))
                .build();
        var instant = Instant.now();
        struct = new TestStruct(mainSchema, instant);
        serializer = codec.createSerializer(new NullOutputStream());
    }

    private static final class TestStruct implements SerializableStruct {

        private final Instant instant;
        private final Schema schema;
        private final Schema amember;
        private final Schema bmember;
        private final Schema cmember;
        private final Schema dmember;
        private final Schema emember;
        private final Schema fmember;
        private final Schema gmember;
        private final Schema hmember;
        private final Schema imember;

        TestStruct(Schema schema, Instant instant) {
            this.instant = instant;
            this.schema = schema;
            amember = schema.member("a");
            bmember = schema.member("b");
            cmember = schema.member("c");
            dmember = schema.member("d");
            emember = schema.member("e");
            fmember = schema.member("f");
            gmember = schema.member("g");
            hmember = schema.member("h");
            imember = schema.member("i");
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeTimestamp(amember, instant);
            serializer.writeTimestamp(bmember, instant);
            serializer.writeTimestamp(cmember, instant);
            serializer.writeTimestamp(dmember, instant);
            serializer.writeTimestamp(emember, instant);
            serializer.writeTimestamp(fmember, instant);
            serializer.writeTimestamp(gmember, instant);
            serializer.writeTimestamp(hmember, instant);
            serializer.writeTimestamp(imember, instant);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }
    }

    @Benchmark
    public void serialize(Blackhole bh) {
        serializer.writeStruct(struct.schema(), struct);
        bh.consume(buff);
    }
}
