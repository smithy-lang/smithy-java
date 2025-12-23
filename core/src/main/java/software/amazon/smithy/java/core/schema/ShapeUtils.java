/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import software.amazon.smithy.java.core.serde.InterceptingSerializer;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.shapes.ShapeType;

public class ShapeUtils {

    private ShapeUtils() {

    }

    /**
     * Create a serializable struct that only serializes members that pass the given predicate.
     *
     * @param schema Structure schema.
     * @param struct Struct to serialize.
     * @param memberPredicate Predicate that takes a member schema.
     * @return the filtered struct.
     */
    public static SerializableStruct withFilteredMembers(
            Schema schema,
            SerializableStruct struct,
            Predicate<Schema> memberPredicate
    ) {
        return new SerializableStruct() {
            @Override
            public Schema schema() {
                return schema;
            }

            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                struct.serializeMembers(new InterceptingSerializer() {
                    @Override
                    protected ShapeSerializer before(Schema schema) {
                        return memberPredicate.test(schema) ? serializer : ShapeSerializer.nullSerializer();
                    }
                });
            }

            @Override
            public <T> T getMemberValue(Schema member) {
                return memberPredicate.test(schema()) ? struct.getMemberValue(member) : null;
            }
        };
    }

    /**
     * Attempts to copy the values from a struct into a shape builder.
     *
     * @param source The shape to copy from.
     * @param sink   The builder to copy into.
     * @throws IllegalArgumentException if the two shapes are incompatible and don't use the same schemas.
     */
    public static void copyShape(SerializableStruct source, ShapeBuilder<?> sink) {
        for (var member : source.schema().members()) {
            var value = source.getMemberValue(member);
            if (value != null) {
                sink.setMemberValue(member, value);
            }
        }
    }

    /**
     * Generates a random instance of a shape using the provided builder.
     *
     * <p>This method populates the shape with randomly generated values for all its members.
     * Required members are always populated, while optional members have a 50% chance of being set.
     * For union types, exactly one member is randomly selected and populated.
     *
     * <p>This is useful for fuzz testing and generating test data.
     *
     * @param shapeBuilder The builder to use for constructing the shape.
     * @param <T>          The type of shape to generate.
     * @return A randomly generated instance of the shape.
     */
    public static <T extends SerializableShape> T generateRandom(ShapeBuilder<T> shapeBuilder) {
        return shapeBuilder.deserialize(new RandomShapeGenerator()).build();
    }

    private static final class RandomShapeGenerator implements ShapeDeserializer {
        @Override
        public boolean readBoolean(Schema schema) {
            return random().nextBoolean();
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            var bytes = new byte[randomInt(100)];
            random().nextBytes(bytes);
            return ByteBuffer.wrap(bytes);
        }

        @Override
        public byte readByte(Schema schema) {
            return (byte) random().nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
        }

        @Override
        public short readShort(Schema schema) {
            return (short) random().nextInt(Short.MIN_VALUE, Short.MAX_VALUE + 1);
        }

        @Override
        public int readInteger(Schema schema) {
            return random().nextInt();
        }

        @Override
        public long readLong(Schema schema) {
            return random().nextLong();
        }

        @Override
        public float readFloat(Schema schema) {
            return random().nextFloat();
        }

        @Override
        public double readDouble(Schema schema) {
            return random().nextDouble();
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            int times = randomInt(3);
            var bigInt = BigInteger.valueOf(random().nextLong());
            for (int i = 0; i < times; i++) {
                bigInt = bigInt.multiply(BigInteger.valueOf(random().nextLong(Long.MAX_VALUE - 100, Long.MAX_VALUE)));
            }
            return bigInt;
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            var bigDecimal = new BigDecimal(readBigInteger(schema));
            int times = randomInt();
            while (times-- > 0) {
                bigDecimal = bigDecimal.divide(new BigDecimal(readBigInteger(schema)), RoundingMode.CEILING);
            }
            return bigDecimal;
        }

        @Override
        public String readString(Schema schema) {
            return HexFormat.of().formatHex(ByteBufferUtils.getBytes(readBlob(schema)));
        }

        @Override
        public Document readDocument() {
            //TODO add more shapes.
            return Document.of(readString(null));
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            return Instant.now();
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            if (schema.type().isShapeType(ShapeType.UNION)) {
                var member = schema.members().get(randomInt(schema.members().size()));
                consumer.accept(state, member, this);
            } else {
                for (var member : schema.members()) {
                    if (member.hasTrait(TraitKey.REQUIRED_TRAIT) || random().nextBoolean()) {
                        consumer.accept(state, member, this);
                    }
                }
            }
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            IntStream.range(0, randomInt()).forEach(i -> consumer.accept(state, this));
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            IntStream.range(0, randomInt()).forEach(i -> consumer.accept(state, readString(schema), this));
        }

        @Override
        public boolean isNull() {
            return random().nextBoolean();
        }

        private static ThreadLocalRandom random() {
            return ThreadLocalRandom.current();
        }

        private static int randomInt() {
            return random().nextInt(10);
        }

        private static int randomInt(int bound) {
            return random().nextInt(bound);
        }
    }
}
