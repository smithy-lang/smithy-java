/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static software.amazon.smithy.java.cbor.CborReadUtil.readByteString;
import static software.amazon.smithy.java.cbor.CborReadUtil.readLong;
import static software.amazon.smithy.java.cbor.CborReadUtil.readTextString;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.java.cbor.CborParser.Token;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class CborParserTest {
    private byte[] cbor;
    private CborParser parser;

    private static byte[] write(Consumer<CborSerializer> consumer) {
        try (
                var stream = new ByteBufferOutputStream();
                var ser = new CborSerializer(new Sink.OutputStreamSink(stream))) {
            try {
                consumer.accept(ser);
            } catch (StopWritingException ignored) {}
            return ByteBufferUtils.getBytes(stream.toByteBuffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class StopWritingException extends RuntimeException {
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private void stopWriting(ShapeSerializer ser) {
        throw new StopWritingException();
    }

    @Test
    public void simple() {
        cbor = write(os -> {
            os.writeInteger(null, 1);
            os.writeInteger(null, 1048576);
            os.writeInteger(null, -1);
        });

        parser = new CborParser(cbor);

        token(Token.POS_INT, 0, 0);
        num(1);

        token(Token.POS_INT, 2, 4);
        num(1048576);

        token(Token.NEG_INT, 6, 0);
        num(-1);

        finished();
    }

    @Test
    public void incompleteImmediate() {
        cbor = write(os -> {
            writeList(os, Integer.MAX_VALUE, this::stopWriting);
        });

        parser = new CborParser(cbor);
        token(Token.START_ARRAY);
        Exception e = assertThrows(BadCborException.class, this::finished);
        assertEquals("incomplete array: expecting " + Integer.MAX_VALUE + " more elements", e.getMessage());
    }

    @Test
    public void bytestring() {
        byte[] bigString = new byte[10];
        Arrays.fill(bigString, (byte) 'A');
        String s = "well howdy there";
        cbor = write(io -> {
            io.writeBlob(null, "hello".getBytes(StandardCharsets.UTF_8));
            io.writeBlob(null, bigString);
            io.writeString(null, s);
        });
        parser = new CborParser(cbor);

        token(Token.BYTE_STRING, 1, 5);
        string("hello");

        token(Token.BYTE_STRING, 7, bigString.length);
        string(bigString);

        token(Token.TEXT_STRING, 18, s.length());
        string(s);

        finished();
    }

    @Test
    public void nestedIndefiniteArrays() {
        cbor = write(os -> {
            writeList(os, -1, list -> {
                list.writeInteger(null, 1);
                writeList(list, -1, nested1 -> {
                    nested1.writeInteger(null, 2);
                    nested1.writeLong(null, Long.MIN_VALUE);
                    writeList(nested1, -1, nested2 -> {
                        nested2.writeString(null, "hello");
                    });
                });
            });
        });

        parser = new CborParser(cbor);
        token(Token.START_ARRAY, 0);

        token(Token.POS_INT, 1, 0);
        num(1);

        token(Token.START_ARRAY, 2);

        token(Token.POS_INT, 3, 0);
        num(2);
        token(Token.NEG_INT, 5, 8);
        num(Long.MIN_VALUE);

        token(Token.START_ARRAY, 13);
        token(Token.TEXT_STRING, 15, 5);
        string("hello");
        token(Token.END_ARRAY, 20);

        token(Token.END_ARRAY, 21);

        token(Token.END_ARRAY, 22);

        finished();
    }

    @Test
    public void map() {
        cbor = write(os -> {
            writeMap(os, 1, map -> {
                map.entry("key", e -> e.writeInteger(null, -1));
            });
            os.writeString(null, "not a key");
        });
        parser = new CborParser(cbor);

        token(Token.START_OBJECT);
        token(Token.KEY, 2, 3);

        token(Token.NEG_INT, 5, 0);
        num(-1);

        token(Token.END_OBJECT);
        token(Token.TEXT_STRING, 7, "not a key".length());

        finished();
    }

    @ValueSource(ints = {1, 1024, 1040000, 19100100})
    @ParameterizedTest
    public void longList(int elements) {
        cbor = write(os -> {
            writeList(os, elements, list -> {
                for (int i = 0; i < elements; i++) {
                    os.writeInteger(null, i);
                }
            });
        });
        parser = new CborParser(cbor);

        token(Token.START_ARRAY);
        for (int i = 0; i < elements; i++) {
            token(Token.POS_INT);
            num(i);
        }
        token(Token.END_ARRAY);
    }

    @ValueSource(ints = {1, 1024, 1040000})
    @ParameterizedTest
    public void longMap(int elements) {
        cbor = write(os -> {
            writeMap(os, elements, map -> {
                for (int i = 0; i < elements; i++) {
                    os.writeString(null, Integer.toString(i));
                    os.writeInteger(null, i);
                }
            });
        });
        parser = new CborParser(cbor);

        token(Token.START_OBJECT);
        for (int i = 0; i < elements; i++) {
            token(Token.KEY);
            string(Integer.toString(i));
            token(Token.POS_INT);
            num(i);
        }
        token(Token.END_OBJECT);
    }

    @Test
    public void collectionsWithIndefiniteMembers() {
        cbor = write(os -> {
            writeMap(os, 1, map -> {
                map.entry("key", e -> {
                    writeList(e, -1, list -> {
                        list.writeString(null, "value1");
                        list.writeString(null, "value2");
                    });
                });
            });

            writeList(os, -1, list -> {
                writeList(list, -1, nested -> {
                    nested.writeString(null, "s");
                });
            });

            writeList(os, 1, list -> {
                writeList(list, -1, nested -> {
                    nested.writeString(null, "s2");
                });
            });

            writeList(os, -1, list -> {
                writeList(list, 3, nested -> {
                    nested.writeInteger(null, 1);
                    nested.writeInteger(null, 2);
                    nested.writeString(null, "three");
                });

                writeMap(list, -1, map -> {
                    map.entry("it's my key!", v -> {
                        writeMap(v, -1, nested -> {
                            nested.entry("it's another nested key", nestedValue -> {
                                nestedValue.writeInteger(null, 91919191);
                            });
                        });
                    });

                    map.entry("still just a key", value -> {
                        writeList(value, -1, nested -> {
                            nested.writeString(null, "array");
                        });
                    });
                });
            });
        });
        parser = new CborParser(cbor);

        token(Token.START_OBJECT);
        token(Token.KEY, 2, 3);
        string("key");
        token(Token.START_ARRAY);
        token(Token.TEXT_STRING);
        string("value1");
        token(Token.TEXT_STRING);
        string("value2");
        token(Token.END_ARRAY);
        token(Token.END_OBJECT);

        token(Token.START_ARRAY);
        token(Token.START_ARRAY);
        token(Token.TEXT_STRING);
        string("s");
        token(Token.END_ARRAY);
        token(Token.END_ARRAY);

        token(Token.START_ARRAY);
        token(Token.START_ARRAY);
        token(Token.TEXT_STRING);
        string("s2");
        token(Token.END_ARRAY);
        token(Token.END_ARRAY);

        token(Token.START_ARRAY);
        token(Token.START_ARRAY);
        token(Token.POS_INT);
        num(1);
        token(Token.POS_INT);
        num(2);
        token(Token.TEXT_STRING);
        string("three");
        token(Token.END_ARRAY);

        token(Token.START_OBJECT);
        token(Token.KEY);
        string("it's my key!");
        token(Token.START_OBJECT);
        token(Token.KEY);
        string("it's another nested key");
        token(Token.POS_INT);
        num(91919191);
        token(Token.END_OBJECT);
        token(Token.KEY);
        string("still just a key");
        token(Token.START_ARRAY);
        token(Token.TEXT_STRING);
        string("array");
        token(Token.END_ARRAY);
        token(Token.END_OBJECT);

        token(Token.END_ARRAY);
        token(Token.FINISHED);
    }

    @Test
    public void nestedCollections() {
        cbor = write(os -> {
            writeMap(os, 2, map -> {
                map.entry("AAA", value -> value.writeInteger(null, 1));
                map.entry("BBB", value -> writeMap(value, 1, nested -> {
                    nested.entry("CCC", nestedValue -> {
                        nestedValue.writeString(null, "DDDDD");
                    });
                }));
            });
        });

        parser = new CborParser(cbor);

        token(Token.START_OBJECT);
        token(Token.KEY, 2, 3);
        string("AAA");
        token(Token.POS_INT, 5, 0);
        num(1);
        token(Token.KEY, 7, 3);
        string("BBB");
        token(Token.START_OBJECT);
        token(Token.KEY, 12, 3);
        string("CCC");
        token(Token.TEXT_STRING, 16, 5);
        string("DDDDD");
        token(Token.END_OBJECT);
        token(Token.END_OBJECT);
        token(Token.FINISHED);
    }

    @Test
    public void array() {
        cbor = write(os -> {
            List<Integer> ints = Arrays.asList(1, 2, 3, 31);
            writeList(os, ints.size(), list -> {
                ints.forEach(i -> list.writeInteger(null, i));
            });
        });

        parser = new CborParser(cbor);
        token(Token.START_ARRAY);
        assertEquals(1, parser.getPosition());

        token(Token.POS_INT);
        num(1);
        token(Token.POS_INT);
        num(2);
        token(Token.POS_INT);
        num(3);
        token(Token.POS_INT);
        num(31);

        token(Token.END_ARRAY);

        token(Token.FINISHED);
    }

    @Test
    public void booleans() {
        cbor = write(os -> {
            os.writeBoolean(null, false);
            os.writeBoolean(null, true);
        });

        parser = new CborParser(cbor);
        token(Token.FALSE);
        token(Token.TRUE);
        token(Token.FINISHED);
    }

    @Test
    public void floats() {
        cbor = write(os -> {
            os.writeDouble(null, 1);
            os.writeFloat(null, 0.1f);
        });

        parser = new CborParser(cbor);
        token(Token.FLOAT);
        token(Token.FLOAT);
        token(Token.FINISHED);
    }

    @Test
    public void bigDecimalLongExponent() {
        byte[][] payloads = new byte[][] {
                new byte[] {-60, -126, 27, 0, 0, 0, 7, -1, -1, -1, -1, 1},
                new byte[] {-60, -126, 59, 0, 0, 0, 7, -1, -1, -1, -1, 1},
                new byte[] {-60, -126, 58, 127, -1, -1, -1, 1}, // 1^-2147483648
                new byte[] {-60, -126, 58, -128, 0, 0, 0, 1} // 1^-2147483649
        };
        for (byte[] payload : payloads) {
            cbor = payload;
            parser = new CborParser(cbor);
            token(Token.BIG_DECIMAL);
            assertThrows(BadCborException.class, () -> CborReadUtil.readBigDecimal(cbor, parser.getPosition()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void incompleteCollection(boolean map) {
        cbor = write(os -> {
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
        parser = new CborParser(cbor);

        token(map ? Token.START_OBJECT : Token.START_ARRAY);
        token(map ? Token.KEY : Token.TEXT_STRING);
        string("hi");
        if (map) {
            token(Token.TEXT_STRING);
            string("hi");
        }
        expectFailure("incomplete " + (map ? "map" : "array"));
    }

    @Test
    public void missingMapValue() {
        cbor = write(os -> {
            writeMap(os, 1, map -> {
                map.entry("hi", this::stopWriting);
            });
        });
        parser = new CborParser(cbor);

        token(Token.START_OBJECT);
        token(Token.KEY);
        string("hi");
        expectFailure("incomplete map");
    }

    @Test
    public void date() {
        Date time = new Date();
        cbor = write(os -> {
            os.writeTimestamp(null, time.toInstant());
        });

        parser = new CborParser(cbor);
        token(Token.EPOCH_F);
        num(Double.doubleToRawLongBits(time.getTime() / 1000d));
        finished();
    }

    @Test
    public void negativeDate() {
        Date d = new Date(-1000);
        cbor = write(os -> {
            os.writeTimestamp(null, d.toInstant());
        });

        parser = new CborParser(cbor);
        token(Token.EPOCH_F);
        num(d.getTime() / 1000d);
        finished();
    }

    private void token(byte token) {
        byte next = parser.advance();
        assertEquals(token, next, "expected " + Token.name(token) + " but got " + Token.name(next));
    }

    private void token(byte token, int position) {
        token(token);
        assertEquals(position, parser.getPosition(), "expected pos " + position + " but was " + parser.getPosition());
    }

    private void token(byte token, int position, int itemLength) {
        token(token, position);
        assertEquals(
                itemLength,
                CborParser.itemLength(parser.getItemLength()),
                "expected len " + itemLength
                        + " but was " + CborParser.itemLength(parser.getItemLength()));
    }

    private void num(double d) {
        num(Double.doubleToRawLongBits(d), Token.POS_INT);
    }

    private void num(long n) {
        num(n, n < 0 ? Token.NEG_INT : Token.POS_INT);
    }

    private void num(long n, byte token) {
        assertEquals(n, readLong(cbor, token, parser.getPosition(), parser.getItemLength()));
    }

    private void string(String s) {
        assertEquals(s, readTextString(cbor, parser.getPosition(), parser.getItemLength()));
    }

    private void string(byte[] b) {
        assertArrayEquals(b, readByteString(cbor, parser.getPosition(), parser.getItemLength()));
    }

    private void finished() {
        token(Token.FINISHED, cbor.length, 0);
    }

    private void lengthIsFinite() {
        assertFalse(CborParser.isIndefinite(parser.getItemLength()));
    }

    private void lengthIsIndefinite() {
        assertTrue(CborParser.isIndefinite(parser.getItemLength()));
    }

    private void expectFailure(String msg) {
        BadCborException e = assertThrows(BadCborException.class, parser::advance);
        assertTrue(e.getMessage().contains(msg), e.getMessage());
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
