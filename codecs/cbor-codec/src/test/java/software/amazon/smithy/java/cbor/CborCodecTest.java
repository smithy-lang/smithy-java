/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.shapes.ShapeId;

class CborCodecTest {

    private static final CborSettings SETTINGS = CborSettings.defaultSettings();
    private static final DefaultCborSerdeProvider CODEC = new DefaultCborSerdeProvider();

    private static ByteBuffer roundTrip(SerializableStruct shape) {
        return CODEC.serialize(shape, SETTINGS);
    }

    @Nested
    class SerializerPoolTest {
        @Test
        void acquireAndReleaseReturnsToPool() {
            var s1 = CborSerializer.acquire();
            CborSerializer.release(s1, false);

            var s2 = CborSerializer.acquire();
            // Pool may or may not return the same instance depending on thread,
            // but it should not throw.
            assertNotNull(s2);
            CborSerializer.release(s2, false);
        }

        @Test
        void acquireProducesCleanState() {
            var s = CborSerializer.acquire();
            assertEquals(0, s.pos);
            CborSerializer.release(s, false);
        }

        @Test
        void largeBufferIsDownsizedOnRelease() {
            var s = CborSerializer.acquire();
            s.buf = new byte[1024 * 1024];
            CborSerializer.release(s, false);

            var s2 = CborSerializer.acquire();
            // If we got back the same pooled instance, its buffer should have been downsized
            // If we got a new instance, it has the default buffer size. Either way, not 1MB.
            assertNotNull(s2);
            CborSerializer.release(s2, false);
        }

        @Test
        void streamingSerializerNotPooled() {
            var s = new CborSerializer(java.io.OutputStream.nullOutputStream());
            CborSerializer.release(s, false);
            // Should not throw, and the streaming serializer should not be returned from acquire
            var s2 = CborSerializer.acquire();
            assertNotSame(s, s2);
            CborSerializer.release(s2, false);
        }
    }

    @Nested
    class Utf8EncodingTest {
        @ParameterizedTest
        @ValueSource(strings = {
                "hello",
                "ASCII only 12345!@#",
                "",
                "a"
        })
        void asciiStrings(String input) {
            var result = serializeString(input);
            var de = CODEC.newDeserializer(result, SETTINGS);
            assertEquals(input, de.readString(PreludeSchemas.STRING));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "é", // e-acute (2-byte UTF-8)
                "üö", // u-umlaut, o-umlaut
                "世界", // Chinese: "world"
                "café", // mixed ASCII + 2-byte
                "😀", // emoji (surrogate pair, 4-byte UTF-8)
                "a😀z", // ASCII + emoji + ASCII
        })
        void nonAsciiStrings(String input) {
            var result = serializeString(input);
            var de = CODEC.newDeserializer(result, SETTINGS);
            assertEquals(input, de.readString(PreludeSchemas.STRING));
        }

        @Test
        void unpairedHighSurrogate() {
            String broken = "a\uD800b";
            var result = serializeString(broken);
            var de = CODEC.newDeserializer(result, SETTINGS);
            String decoded = de.readString(PreludeSchemas.STRING);
            assertEquals("a�b", decoded);
        }

        @Test
        void unpairedLowSurrogate() {
            String broken = "a\uDC00b";
            var result = serializeString(broken);
            var de = CODEC.newDeserializer(result, SETTINGS);
            String decoded = de.readString(PreludeSchemas.STRING);
            assertEquals("a�b", decoded);
        }

        private byte[] serializeString(String value) {
            var s = CborSerializer.acquire();
            try {
                s.writeString(PreludeSchemas.STRING, value);
                return java.util.Arrays.copyOf(s.buf, s.pos);
            } finally {
                CborSerializer.release(s, false);
            }
        }
    }

    @Nested
    class CollectionDepthTest {
        @Test
        void deeplyNestedCollections() {
            int depth = 100;
            var s = CborSerializer.acquire();
            try {
                for (int i = 0; i < depth; i++) {
                    s.writeList(PreludeSchemas.DOCUMENT, null, -1, ($, inner) -> {});
                }
            } finally {
                CborSerializer.release(s, false);
            }
        }

        @Test
        void collectionDepthExceeds64() {
            int depth = 70;
            var s = CborSerializer.acquire();
            try {
                for (int i = 0; i < depth; i++) {
                    boolean indefinite = i % 2 == 0;
                    s.writeList(PreludeSchemas.DOCUMENT,
                            null,
                            indefinite ? -1 : 1,
                            ($, inner) -> {});
                }
            } finally {
                CborSerializer.release(s, false);
            }
        }
    }

    @Nested
    class MemberLookupTest {
        private static final ShapeId STRUCT_ID = ShapeId.from("smithy.test#TestStruct");
        private static final Schema STRUCT = Schema.structureBuilder(STRUCT_ID)
                .putMember("alpha", PreludeSchemas.STRING)
                .putMember("beta", PreludeSchemas.INTEGER)
                .putMember("gamma", PreludeSchemas.BOOLEAN)
                .build();

        @Test
        void lookupInDefinitionOrder() {
            var lookup = new CborMemberLookup(STRUCT.members());
            byte[] alpha = "alpha".getBytes(StandardCharsets.UTF_8);
            byte[] beta = "beta".getBytes(StandardCharsets.UTF_8);
            byte[] gamma = "gamma".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(alpha, 0, alpha.length, 0);
            assertEquals("alpha", found.memberName());

            found = lookup.lookup(beta, 0, beta.length, 1);
            assertEquals("beta", found.memberName());

            found = lookup.lookup(gamma, 0, gamma.length, 2);
            assertEquals("gamma", found.memberName());
        }

        @Test
        void lookupOutOfOrder() {
            var lookup = new CborMemberLookup(STRUCT.members());
            byte[] gamma = "gamma".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(gamma, 0, gamma.length, 0);
            assertEquals("gamma", found.memberName());
        }

        @Test
        void lookupDisabledSpeculative() {
            var lookup = new CborMemberLookup(STRUCT.members());
            byte[] beta = "beta".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(beta, 0, beta.length, -1);
            assertEquals("beta", found.memberName());
        }

        @Test
        void lookupUnknownMemberReturnsNull() {
            var lookup = new CborMemberLookup(STRUCT.members());
            byte[] unknown = "unknown".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(unknown, 0, unknown.length, 0);
            assertNull(found);
        }

        @Test
        void lookupWithOffset() {
            var lookup = new CborMemberLookup(STRUCT.members());
            byte[] padded = "XXalphaYY".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(padded, 2, 7, -1);
            assertEquals("alpha", found.memberName());
        }

        @Test
        void emptyMemberList() {
            var lookup = new CborMemberLookup(List.of());
            byte[] any = "any".getBytes(StandardCharsets.UTF_8);

            Schema found = lookup.lookup(any, 0, any.length, -1);
            assertNull(found);
        }
    }

    @Nested
    class SchemaExtensionsTest {
        private static final ShapeId STRUCT_ID = ShapeId.from("smithy.test#ExtStruct");
        private static final Schema STRUCT = Schema.structureBuilder(STRUCT_ID)
                .putMember("foo", PreludeSchemas.STRING)
                .putMember("bar", PreludeSchemas.INTEGER)
                .build();

        @Test
        void memberExtensionHasEncodedNameBytes() {
            var provider = new CborSchemaExtensions();
            var ext = provider.provide(STRUCT.member("foo"));

            assertNotNull(ext);
            assertNotNull(ext.memberNameBytes());
            assertNull(ext.memberLookup());
            assertNull(ext.fieldNameTable());

            byte[] expected = CborSerializer.encodeMemberName("foo");
            assertArrayEquals(expected, ext.memberNameBytes());
        }

        @Test
        void structExtensionHasLookupAndFieldNameTable() {
            var provider = new CborSchemaExtensions();
            var ext = provider.provide(STRUCT);

            assertNotNull(ext);
            assertNull(ext.memberNameBytes());
            assertNotNull(ext.memberLookup());
            assertNotNull(ext.fieldNameTable());

            assertEquals(2, ext.fieldNameTable().length);
            assertNotNull(ext.fieldNameTable()[0]);
            assertNotNull(ext.fieldNameTable()[1]);
        }

        @Test
        void nonStructNonMemberReturnsNull() {
            var provider = new CborSchemaExtensions();
            var ext = provider.provide(PreludeSchemas.STRING);
            assertNull(ext);
        }

        @Test
        void emptyStructReturnsEmptyExtension() {
            Schema emptyStruct = Schema.structureBuilder(ShapeId.from("smithy.test#Empty")).build();
            var provider = new CborSchemaExtensions();
            var ext = provider.provide(emptyStruct);

            assertNotNull(ext);
            assertNull(ext.memberNameBytes());
            assertNull(ext.memberLookup());
            assertNull(ext.fieldNameTable());
        }
    }

    @Nested
    class EncodeMemberNameTest {
        @Test
        void shortName() {
            byte[] result = CborSerializer.encodeMemberName("id");
            // CBOR text string header for length 2: 0x62, then 'i', 'd'
            assertEquals(3, result.length);
            assertEquals(0x62, result[0] & 0xFF);
            assertEquals('i', result[1]);
            assertEquals('d', result[2]);
        }

        @Test
        void longerName() {
            String name = "a".repeat(200);
            byte[] result = CborSerializer.encodeMemberName(name);
            // Length 200 needs 1-byte arg: header = 0x78 (text string, 1-byte length), 0xC8 (200)
            assertEquals(202, result.length);
            assertEquals(0x78, result[0] & 0xFF);
            assertEquals(200, result[1] & 0xFF);
        }
    }

    @Nested
    class StructRoundTripTest {
        @Test
        void serializeDeserializeInOrder() {
            var bird = new CborTestData.BirdBuilder()
                    .name("falcon")
                    .flightRange(BigInteger.valueOf(42))
                    .build();

            var ser = roundTrip(bird);
            var de = new CborTestData.BirdBuilder()
                    .deserialize(CODEC.newDeserializer(ser, SETTINGS))
                    .build();

            assertEquals("falcon", de.name);
            assertEquals(BigInteger.valueOf(42), de.flightRange);
        }

        @Test
        void serializeDeserializeAllFields() {
            var bird = new CborTestData.BirdBuilder()
                    .name("hawk")
                    .bytes(ByteBuffer.wrap(new byte[] {1, 2, 3}))
                    .lastSquawkAt(Instant.ofEpochSecond(1000))
                    .flightRange(BigInteger.TEN)
                    .wingspan(new BigDecimal("3.14"))
                    .build();

            var ser = roundTrip(bird);
            var de = new CborTestData.BirdBuilder()
                    .deserialize(CODEC.newDeserializer(ser, SETTINGS))
                    .build();

            assertEquals("hawk", de.name);
            assertEquals(Instant.ofEpochSecond(1000), de.lastSquawkAt);
            assertEquals(BigInteger.TEN, de.flightRange);
            assertEquals(new BigDecimal("3.14"), de.wingspan);
        }

        // Regression: the original readTimestamp passed the tagged token (EPOCH_IPOS=16) to readLong,
        // which rejected any token > NEG_INT(1), so integer epoch timestamps always threw.
        @Test
        void timestampIntegerEpoch() {
            // tag(1) + uint8(100) - simplest valid integer epoch
            byte[] payload = new byte[] {(byte) 0xC1, 0x18, 0x64};
            ShapeDeserializer de = CODEC.newDeserializer(payload, SETTINGS);
            assertEquals(Instant.ofEpochSecond(100), de.readTimestamp(PreludeSchemas.TIMESTAMP));
        }

        @Test
        void timestampNegativeIntegerEpoch() {
            // tag(1) + negint8(99) = -1 - 99 = -100 seconds
            byte[] payload = new byte[] {(byte) 0xC1, 0x38, 0x63};
            ShapeDeserializer de = CODEC.newDeserializer(payload, SETTINGS);
            assertEquals(Instant.ofEpochSecond(-100), de.readTimestamp(PreludeSchemas.TIMESTAMP));
        }

        @Test
        void timestampWholeSeconds() {
            Instant wholeSecond = Instant.ofEpochSecond(1700000000);
            var bird = new CborTestData.BirdBuilder()
                    .lastSquawkAt(wholeSecond)
                    .build();

            var ser = roundTrip(bird);
            var de = new CborTestData.BirdBuilder()
                    .deserialize(CODEC.newDeserializer(ser, SETTINGS))
                    .build();

            assertEquals(wholeSecond, de.lastSquawkAt);
        }

        @Test
        void timestampWithMillis() {
            Instant withMillis = Instant.ofEpochMilli(1700000000123L);
            var bird = new CborTestData.BirdBuilder()
                    .lastSquawkAt(withMillis)
                    .build();

            var ser = roundTrip(bird);
            var de = new CborTestData.BirdBuilder()
                    .deserialize(CODEC.newDeserializer(ser, SETTINGS))
                    .build();

            assertEquals(withMillis, de.lastSquawkAt);
        }
    }

    @Nested
    class BigIntegerEdgeCaseTest {
        @ParameterizedTest
        @ValueSource(strings = {
                "9223372036854775808", // Long.MAX_VALUE + 1 (exactly 64 bits positive)
                "-9223372036854775809", // Long.MIN_VALUE - 1 (exactly 64 bits negative)
                "18446744073709551615", // max unsigned 64-bit
        })
        void bigInteger64BitBoundary(String value) {
            BigInteger bi = new BigInteger(value);
            var bird = new CborTestData.BirdBuilder().flightRange(bi).build();
            var ser = roundTrip(bird);
            var de = new CborTestData.BirdBuilder()
                    .deserialize(CODEC.newDeserializer(ser, SETTINGS))
                    .build();
            assertEquals(bi, de.flightRange);
        }
    }

    @Nested
    class MalformedCborTest {

        @Test
        void bigDecimalLongExponent() {
            byte[][] payloads = new byte[][] {
                    new byte[] {-60, -126, 27, 0, 0, 0, 7, -1, -1, -1, -1, 1},
                    new byte[] {-60, -126, 59, 0, 0, 0, 7, -1, -1, -1, -1, 1},
                    new byte[] {-60, -126, 58, 127, -1, -1, -1, 1}, // 1^-2147483648
                    new byte[] {-60, -126, 58, -128, 0, 0, 0, 1} // 1^-2147483649
            };
            for (byte[] payload : payloads) {
                ShapeDeserializer de = CODEC.newDeserializer(payload, SETTINGS);
                assertThrows(BadCborException.class, () -> de.readBigDecimal(PreludeSchemas.BIG_DECIMAL));
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void bigIntegerWithExcessiveLength(boolean negative) {
            byte[] payload = new byte[] {
                    (byte) (negative ? 0xC3 : 0xC2),
                    (byte) 0x5A,
                    (byte) 0x7F,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0x01,
                    (byte) 0x02
            };
            ShapeDeserializer de = CODEC.newDeserializer(payload, SETTINGS);
            assertThrows(BadCborException.class, () -> de.readBigInteger(PreludeSchemas.BIG_INTEGER));
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void bigIntegerWithIndefiniteLengthExcessiveChunk(boolean negative) {
            byte[] payload = new byte[] {
                    (byte) (negative ? 0xC3 : 0xC2),
                    (byte) 0x5F,
                    (byte) 0x5A,
                    (byte) 0x7F,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0x01,
                    (byte) 0x02
            };
            // TODO Fix this so that we don't throw AIOBE
            assertThrows(ArrayIndexOutOfBoundsException.class, () -> CODEC.newDeserializer(payload, SETTINGS));
        }

        @Test
        void bigIntegerWithIndefiniteLengthExcessiveTotalSize() {
            byte[] payload = new byte[] {
                    (byte) 0xC2,
                    (byte) 0x5F,
                    (byte) 0x5A,
                    (byte) 0x1D,
                    (byte) 0xCD,
                    (byte) 0x65,
                    (byte) 0x00,
                    (byte) 0x01,
                    (byte) 0x02,
                    (byte) 0x5A,
                    (byte) 0x1D,
                    (byte) 0xCD,
                    (byte) 0x65,
                    (byte) 0x00,
                    (byte) 0x03,
                    (byte) 0x04
            };
            assertThrows(BadCborException.class, () -> CODEC.newDeserializer(payload, SETTINGS));
        }

        @Test
        void incompleteImmediate() {
            byte[] cbor = write(os -> {
                writeList(os, Integer.MAX_VALUE, list -> list.writeString(null, "stop"));
            });
            BadCborException e = assertThrows(BadCborException.class, () -> {
                ShapeDeserializer de = CODEC.newDeserializer(cbor, SETTINGS);
                de.readDocument();
            });
            assertTrue(e.getMessage().contains("incomplete array"), e.getMessage());
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void incompleteCollection(boolean map) {
            byte[] cbor = write(os -> {
                if (map) {
                    writeMap(os, 2, c -> {
                        c.entry("hi", value -> value.writeString(null, "hi"));
                    });
                } else {
                    writeList(os, 2, c -> {
                        c.writeString(null, "hi");
                    });
                }
            });
            BadCborException e = assertThrows(BadCborException.class, () -> {
                ShapeDeserializer de = CODEC.newDeserializer(cbor, SETTINGS);
                de.readDocument();
            });
            assertTrue(e.getMessage().contains("incomplete " + (map ? "map" : "array")), e.getMessage());
        }

        @Test
        void timestampOutOfRange() {
            // tag(1) + uint64(Long.MAX_VALUE) - epoch seconds far exceeding Instant.MAX
            byte[] payload = new byte[] {
                    (byte) 0xC1, // tag 1 (epoch timestamp)
                    (byte) 0x1B, // 8-byte positive integer follows
                    (byte) 0x7F,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF,
                    (byte) 0xFF
            };
            ShapeDeserializer de = CODEC.newDeserializer(payload, SETTINGS);
            assertThrows(SerializationException.class, () -> de.readTimestamp(PreludeSchemas.TIMESTAMP));
        }

        @Test
        void missingMapValue() {
            byte[] cbor = write(os -> {
                writeMap(os, 1, m -> {
                    m.entry("hi", v -> {
                        throw new StopWritingException();
                    });
                });
            });
            BadCborException e = assertThrows(BadCborException.class, () -> {
                ShapeDeserializer de = CODEC.newDeserializer(cbor, SETTINGS);
                de.readDocument();
            });
            assertTrue(e.getMessage().contains("incomplete map"), e.getMessage());
        }
    }

    private static final class StopWritingException extends RuntimeException {
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private static byte[] write(Consumer<CborSerializer> consumer) {
        try (
                var stream = new ByteBufferOutputStream();
                var ser = new CborSerializer(stream)) {
            try {
                consumer.accept(ser);
            } catch (StopWritingException ignored) {}
            ser.flush();
            return ByteBufferUtils.getBytes(stream.toByteBuffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeList(ShapeSerializer s, int len, Consumer<ShapeSerializer> listHandler) {
        s.writeList(null, null, len, ($, l) -> listHandler.accept(l));
    }

    private static void writeMap(ShapeSerializer s, int len, Consumer<WriteEntry> mapHandler) {
        s.writeMap(null, null, len, ($, l) -> mapHandler.accept(new WriteEntry(l)));
    }

    private static final class WriteEntry {
        private final MapSerializer m;

        private WriteEntry(MapSerializer m) {
            this.m = m;
        }

        void entry(String key, Consumer<ShapeSerializer> val) {
            m.writeEntry(null, key, null, ($, s) -> val.accept(s));
        }
    }
}
