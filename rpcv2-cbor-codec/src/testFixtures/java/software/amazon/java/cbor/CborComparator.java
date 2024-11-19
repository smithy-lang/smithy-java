/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.java.cbor;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static software.amazon.smithy.java.cbor.CborReadUtil.readTextString;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.cbor.CborParser;
import software.amazon.smithy.java.cbor.CborReadUtil;
import software.amazon.smithy.java.io.ByteBufferUtils;

public class CborComparator {

    public static void assertEquals(ByteBuffer expected, ByteBuffer actual) {
        byte[] expectedBytes = ByteBufferUtils.getBytes(expected);
        byte[] actualBytes = ByteBufferUtils.getBytes(actual);
        if (Arrays.equals(expectedBytes, actualBytes)) {
            return;
        }
        assertThat(CborValue.parse(actualBytes))
            .usingRecursiveComparison()
            .isEqualTo(CborValue.parse(expectedBytes));

    }

    private abstract static sealed class CborValue<T extends CborValue<T>> {

        private static CborValue<?> parse(byte[] buf) {
            try {
                return parse0(buf);
            } catch (Exception e) {
                // to ensure a stack trace is gathered
                throw new RuntimeException(e);
            }
        }

        private static CborValue<?> parse0(byte[] buf) {
            var parser = new CborParser(buf);
            var context = new ArrayDeque<CborValue<?>>();
            var keys = new ArrayDeque<String>();
            CborValue<?> root = null;
            byte token;
            while ((token = parser.advance()) != CborParser.Token.FINISHED) {
                switch (token) {
                    case CborParser.Token.KEY ->
                        keys.addLast(readTextString(buf, parser.getPosition(), parser.getItemLength()));
                    case CborParser.Token.START_OBJECT -> context.addLast(new MapValue());
                    case CborParser.Token.START_ARRAY -> context.addLast(new ListValue());
                    case CborParser.Token.END_ARRAY, CborParser.Token.END_OBJECT -> {
                        var top = Objects.requireNonNull(context.pollLast());
                        if (context.isEmpty()) {
                            root = top;
                        } else {
                            add(keys, context, top);
                        }
                    }
                    case CborParser.Token.NULL -> add(keys, context, new NullValue());
                    case CborParser.Token.TEXT_STRING -> add(keys, context, new StringValue(buf, parser));
                    case CborParser.Token.POS_INT, CborParser.Token.NEG_INT ->
                        add(keys, context, new IntValue(buf, token, parser));
                    case CborParser.Token.FLOAT -> add(keys, context, new FloatValue(buf, token, parser));
                    case CborParser.Token.TRUE, CborParser.Token.FALSE -> add(keys, context, new BooleanValue(token));
                    case CborParser.Token.EPOCH_F, CborParser.Token.EPOCH_INEG, CborParser.Token.EPOCH_IPOS -> add(
                        keys,
                        context,
                        new TimeValue(buf, token, parser)
                    );
                    case CborParser.Token.BYTE_STRING -> add(keys, context, new BlobValue(buf, parser));
                    default -> throw new RuntimeException("can't handle " + CborParser.Token.name(token));
                }
            }
            return root;
        }

        private static void add(Deque<String> keys, Deque<CborValue<?>> context, CborValue<?> value) {
            var top = context.peekLast();
            if (top instanceof MapValue v) {
                v.put(Objects.requireNonNull(keys.pollLast()), value);
            } else if (top instanceof ListValue v) {
                v.add(value);
            } else {
                throw new RuntimeException("Can't add to a " + top.getClass());
            }
        }

        private static final class MapValue extends CborValue<MapValue> {
            private final Map<String, CborValue<?>> map;

            private MapValue() {
                this.map = new HashMap<>();
            }

            public void put(String key, CborValue<?> value) {
                map.put(key, value);
            }

            @Override
            public String toString() {
                return "Map{" + map + "}";
            }
        }

        private static final class ListValue extends CborValue<ListValue> {
            private final List<CborValue<?>> values = new ArrayList<>();

            public void add(CborValue<?> value) {
                values.add(value);
            }

            @Override
            public String toString() {
                return "List{" + values + "}";
            }
        }

        private static final class NullValue extends CborValue<NullValue> {

            @Override
            public String toString() {
                return "Null";
            }
        }

        private static final class StringValue extends CborValue<StringValue> {
            private final String value;

            private StringValue(byte[] buf, CborParser parser) {
                this.value = CborReadUtil.readTextString(buf, parser.getPosition(), parser.getItemLength());
            }

            @Override
            public String toString() {
                return "String{" + value + "}";
            }
        }

        private static final class IntValue extends CborValue<IntValue> {
            private final long value;

            IntValue(byte[] buf, byte type, CborParser parser) {
                if (type > CborParser.Token.NEG_INT) {
                    throw new RuntimeException("can't read " + CborParser.Token.name(type) + " as long");
                }

                int pos = parser.getPosition();
                int len = parser.getItemLength();
                long val = CborReadUtil.readLong(buf, type, pos, len);
                if (len < 8) {
                    this.value = val;
                } else if (type == CborParser.Token.POS_INT) {
                    this.value = val < 0 ? Long.MAX_VALUE : val;
                } else {
                    this.value = val < 0 ? val : Long.MIN_VALUE;
                }
            }

            @Override
            public String toString() {
                return "Int{" + value + "}";
            }
        }

        private static final class FloatValue extends CborValue<FloatValue> {
            private final double value;

            FloatValue(byte[] buf, byte type, CborParser parser) {
                int len = parser.getItemLength();
                long raw = CborReadUtil.readLong(buf, type, parser.getPosition(), parser.getItemLength());
                if (len == 8) {
                    value = Double.longBitsToDouble(raw);
                } else if (len == 4) {
                    value = Float.intBitsToFloat((int) raw);
                } else {
                    throw new RuntimeException("Can't handle fp of len " + len);
                }
            }

            @Override
            public String toString() {
                return "Float{" + value + "}";
            }
        }

        private static final class BooleanValue extends CborValue<BooleanValue> {
            private final boolean bool;

            BooleanValue(byte type) {
                this.bool = type == CborParser.Token.TRUE;
            }

            @Override
            public String toString() {
                return "Boolean{" + bool + "}";
            }
        }

        private static final class TimeValue extends CborValue<TimeValue> {
            private final long time;

            TimeValue(byte[] buf, byte type, CborParser parser) {
                byte actual = (byte) (type ^ CborParser.Token.TAG_FLAG);
                if (actual <= CborParser.Token.NEG_INT) {
                    time = new IntValue(buf, actual, parser).value * 1000;
                } else if (actual == CborParser.Token.FLOAT) {
                    time = Math.round(new FloatValue(buf, actual, parser).value * 1000d);
                } else {
                    throw new RuntimeException("not a timestamp: " + CborParser.Token.name(type));
                }
            }

            @Override
            public String toString() {
                return "Time{" + time + "}";
            }
        }

        private static final class BlobValue extends CborValue<BlobValue> {
            private final byte[] bytes;

            BlobValue(byte[] buf, CborParser parser) {
                this.bytes = CborReadUtil.readByteString(buf, parser.getPosition(), parser.getItemLength());
            }

            @Override
            public String toString() {
                return "Blob{" + HexFormat.of().formatHex(bytes) + "}";
            }
        }
    }
}
