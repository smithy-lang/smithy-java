/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.rpcv2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.runtime.cbor.CborReadUtil.readTextString;
import static software.amazon.smithy.java.runtime.io.ByteBufferUtils.getBytes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.protocoltests.harness.HttpClientRequestTests;
import software.amazon.smithy.java.protocoltests.harness.HttpClientResponseTests;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTest;
import software.amazon.smithy.java.protocoltests.harness.ProtocolTestFilter;
import software.amazon.smithy.java.protocoltests.harness.TestType;
import software.amazon.smithy.java.runtime.cbor.CborParser;
import software.amazon.smithy.java.runtime.cbor.CborParser.Token;
import software.amazon.smithy.java.runtime.cbor.CborReadUtil;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

@ProtocolTest(
    service = "smithy.protocoltests.rpcv2Cbor#RpcV2Protocol",
    testType = TestType.CLIENT
)
@ProtocolTestFilter(skipOperations = {})
public class RpcV2CborProtocolTests {
    @HttpClientRequestTests
    @ProtocolTestFilter(
        skipTests = {
            // this test is broken and needs to be fixed in smithy
            "RpcV2CborClientPopulatesDefaultValuesInInput",
            // clientOptional is not respected for client-generated shapes yet
            "RpcV2CborClientSkipsTopLevelDefaultValuesInInput",
            "RpcV2CborClientUsesExplicitlyProvidedMemberValues",
            "RpcV2CborClientIgnoresNonTopLevelDefaultsOnMembersWithClientOptional",
            "RpcV2CborClientUsesExplicitlyProvidedMemberValuesOverDefaults",
        }
    )
    public void requestTest(DataStream expected, DataStream actual) {
        var ex = parse(getBytes(expected.waitForByteBuffer()));
        var ac = parse(getBytes(actual.waitForByteBuffer()));
        assertEquals(ex, ac);
    }

    @HttpClientResponseTests
    public void responseTest(Runnable test) {
        test.run();
    }

    private static CborValue parse(byte[] buf) {
        try {
            return parse0(buf);
        } catch (Exception e) {
            // to ensure a stack trace is gathered
            throw new RuntimeException(e);
        }
    }

    private static CborValue parse0(byte[] buf) {
        var parser = new CborParser(buf);
        var context = new ArrayDeque<CborValue>();
        var keys = new ArrayDeque<String>();
        CborValue root = null;
        byte token;
        while ((token = parser.advance()) != Token.FINISHED) {
            switch (token) {
                case Token.KEY -> keys.addLast(readTextString(buf, parser.getPosition(), parser.getItemLength()));
                case Token.START_OBJECT -> context.addLast(new MapValue());
                case Token.START_ARRAY -> context.addLast(new ListValue());
                case Token.END_ARRAY, Token.END_OBJECT -> {
                    var top = Objects.requireNonNull(context.pollLast());
                    if (context.isEmpty()) {
                        root = top;
                    } else {
                        add(keys, context, top);
                    }
                }
                case Token.NULL -> add(keys, context, new NullValue());
                case Token.TEXT_STRING -> add(keys, context, new StringValue(buf, parser));
                case Token.POS_INT, Token.NEG_INT -> add(keys, context, new IntValue(buf, token, parser));
                case Token.FLOAT -> add(keys, context, new FloatValue(buf, token, parser));
                case Token.TRUE, Token.FALSE -> add(keys, context, new BooleanValue(token));
                case Token.EPOCH_F, Token.EPOCH_INEG, Token.EPOCH_IPOS -> add(
                    keys,
                    context,
                    new TimeValue(buf, token, parser)
                );
                case Token.BYTE_STRING -> add(keys, context, new BlobValue(buf, parser));
                default -> throw new RuntimeException("can't handle " + Token.name(token));
            }
        }
        return root;
    }

    private static void add(Deque<String> keys, Deque<CborValue> context, CborValue value) {
        var top = context.peekLast();
        if (top instanceof MapValue v) {
            v.put(Objects.requireNonNull(keys.pollLast()), value);
        } else if (top instanceof ListValue v) {
            v.add(value);
        } else {
            throw new RuntimeException("Can't add to a " + top.getClass());
        }
    }

    sealed static abstract class CborValue<T extends CborValue<T>> permits BooleanValue, BlobValue, FloatValue,
        IntValue, ListValue, MapValue, NullValue, StringValue, TimeValue {

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object o) {
            assertEquals(this.getClass(), o.getClass());
            compare((T) o);
            return true;
        }

        abstract void compare(T v);
    }

    private static final class MapValue extends CborValue<MapValue> {
        private final Map<String, CborValue<?>> map;

        private MapValue() {
            this.map = new HashMap<>();
        }

        public void put(String Key, CborValue<?> value) {
            map.put(Key, value);
        }

        @Override
        public void compare(MapValue value) {
            assertEquals(map, value.map);
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
        public void compare(ListValue value) {
            assertEquals(values, value.values);
        }

        @Override
        public String toString() {
            return "List{" + values + "}";
        }
    }

    private static final class NullValue extends CborValue<NullValue> {
        @Override
        void compare(NullValue v) {}

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
        public void compare(StringValue value) {
            assertEquals(this.value, value.value);
        }

        @Override
        public String toString() {
            return "String{" + value + "}";
        }
    }

    private static final class IntValue extends CborValue<IntValue> {
        private final long value;

        IntValue(byte[] buf, byte type, CborParser parser) {
            if (type > Token.NEG_INT) {
                throw new RuntimeException("can't read " + Token.name(type) + " as long");
            }

            int pos = parser.getPosition();
            int len = parser.getItemLength();
            long val = CborReadUtil.readLong(buf, type, pos, len);
            if (len < 8) {
                this.value = val;
            } else if (type == Token.POS_INT) {
                this.value = val < 0 ? Long.MAX_VALUE : val;
            } else {
                this.value = val < 0 ? val : Long.MIN_VALUE;
            }
        }

        @Override
        public void compare(IntValue value) {
            assertEquals(this.value, value.value);
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
        void compare(FloatValue v) {
            assertEquals(value, v.value);
        }

        @Override
        public String toString() {
            return "Float{" + value + "}";
        }
    }

    private static final class BooleanValue extends CborValue<BooleanValue> {
        private final boolean bool;

        BooleanValue(byte type) {
            this.bool = type == Token.TRUE;
        }

        @Override
        public void compare(BooleanValue value) {
            assertEquals(bool, value.bool);
        }

        @Override
        public String toString() {
            return "Boolean{" + bool + "}";
        }
    }

    private static final class TimeValue extends CborValue<TimeValue> {
        private final long time;

        TimeValue(byte[] buf, byte type, CborParser parser) {
            byte actual = (byte) (type ^ Token.TAG_FLAG);
            if (actual <= Token.NEG_INT) {
                time = new IntValue(buf, actual, parser).value * 1000;
            } else if (actual == Token.FLOAT) {
                time = Math.round(new FloatValue(buf, actual, parser).value * 1000d);
            } else {
                throw new RuntimeException("not a timestamp: " + Token.name(type));
            }
        }

        @Override
        void compare(TimeValue v) {
            assertEquals(time, v.time);
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
        public void compare(BlobValue value) {
            assertArrayEquals(bytes, value.bytes);
        }

        @Override
        public String toString() {
            return "Blob{" + HexFormat.of().formatHex(bytes) + "}";
        }
    }
}
