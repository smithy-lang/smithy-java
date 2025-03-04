/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.serde;

import com.jsoniter.JsonIterator;
import com.jsoniter.ValueType;
import com.jsoniter.spi.JsonException;
import com.jsoniter.spi.Slice;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.TimestampResolver;

// TODO: it might be possible to re-use some of the existing parsing.
// TODO: This might be better as a document implementation? Might also work with dynamic then?
public class CliArgumentDeserializer implements ShapeDeserializer {
    private final Map<Schema, List<String>> memberArgs = new HashMap<>();

    public void putArgs(Schema schema, List<String> args) {
        memberArgs.put(schema, args);
    }

    @Override
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
        for (var membersSchema : schema.members()) {
            var args = memberArgs.get(membersSchema);
            if (args == null) {
                return;
            }
            consumer.accept(state, membersSchema, new NestedDeserializer(args));
        }
    }

    @Override
    public boolean readBoolean(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public ByteBuffer readBlob(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public byte readByte(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public short readShort(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public int readInteger(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public long readLong(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public float readFloat(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public double readDouble(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public BigInteger readBigInteger(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public BigDecimal readBigDecimal(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public String readString(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public Document readDocument() {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
        throw new UnsupportedOperationException("not supported");
    }

    @Override
    public boolean isNull() {
        throw new UnsupportedOperationException("not supported");
    }

    // TODO: this seems a bit inefficient
    private record NestedDeserializer(List<String> args) implements ShapeDeserializer {

        @Override
        public boolean readBoolean(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for boolean but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readBoolean(schema);
            }
        }

        // TODO: Should we allow reading from a file for these?
        @Override
        public ByteBuffer readBlob(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for blob but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readBlob(schema);
            }
        }

        @Override
        public byte readByte(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for byte but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readByte(schema);
            }
        }

        @Override
        public short readShort(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for short but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readShort(schema);
            }
        }

        @Override
        public int readInteger(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for integer but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readShort(schema);
            }
        }

        @Override
        public long readLong(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for long but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readShort(schema);
            }
        }

        @Override
        public float readFloat(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for float but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readFloat(schema);
            }
        }

        @Override
        public double readDouble(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for double but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readDouble(schema);
            }
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for BigInteger but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readBigInteger(schema);
            }
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for BigDecimal but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readBigDecimal(schema);
            }
        }

        @Override
        public String readString(Schema schema) {
            if (args.size() != 1) {
                throw new IllegalArgumentException("Expected 1 argument for boolean but got " + args.size());
            }
            try (var iter = new JsonIterDeserializer(args.get(0).getBytes(StandardCharsets.UTF_8))) {
                return iter.readString(schema);
            }
        }

        @Override
        public Document readDocument() {
            // TODO: What should this look like?
            throw new UnsupportedOperationException("Document inputs not supported");
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            return null;
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            // TODO: Needs to determine if shorthand or not
            throw new IllegalArgumentException("Unsupported!");
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            // TODO: Needs to determine if shorthand or not
            throw new IllegalArgumentException("Unsupported!");
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            // TODO: Needs to determine if shorthand or not
            throw new IllegalArgumentException("Unsupported!");
        }

        @Override
        public boolean isNull() {
            return false;
        }
    }

    /**
     * TODO: Once this is re-added just use this directly?
     * This is all pulled from the json iter branch.
     */
    static final class JsonIterDeserializer implements ShapeDeserializer {

        private final JsonIterator iter;
        private final TimestampResolver timestampResolver;
        private final byte[] data;

        JsonIterDeserializer(byte[] data) {
            this.iter = JsonIterator.parse(data);
            // TODO: This required making the `UserTimestampFormatTrait()` method public, that may not be right
            this.timestampResolver = new TimestampResolver.UseTimestampFormatTrait(
                    TimestampFormatter.Prelude.EPOCH_SECONDS);
            this.data = data;
        }

        @Override
        public void close() {
            try {
                iter.close();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public ByteBuffer readBlob(Schema schema) {
            try {
                Slice content = iter.readStringAsSlice();
                ByteBuffer buffer = ByteBuffer.wrap(content.data(), content.head(), content.tail() - content.head());
                return Base64.getDecoder().decode(buffer);
            } catch (JsonException | IOException | IllegalArgumentException e) {
                throw new SerializationException("Invalid base64 encoded string");
            }
        }

        @Override
        public byte readByte(Schema schema) {
            try {
                return (byte) iter.readShort();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public short readShort(Schema schema) {
            try {
                return iter.readShort();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public int readInteger(Schema schema) {
            try {
                return iter.readInt();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public long readLong(Schema schema) {
            try {
                return iter.readLong();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public float readFloat(Schema schema) {
            try {
                if (iter.whatIsNext() == ValueType.STRING) {
                    return switch (iter.readString()) {
                        case "NaN" -> Float.NaN;
                        case "-Infinity" -> Float.NEGATIVE_INFINITY;
                        case "Infinity" -> Float.POSITIVE_INFINITY;
                        default -> throw new SerializationException("Expected float, received unrecognized string");
                    };
                }
                return iter.readFloat();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public double readDouble(Schema schema) {
            try {
                if (iter.whatIsNext() == ValueType.STRING) {
                    return switch (iter.readString()) {
                        case "NaN" -> Double.NaN;
                        case "-Infinity" -> Double.NEGATIVE_INFINITY;
                        case "Infinity" -> Double.POSITIVE_INFINITY;
                        default -> throw new SerializationException("Expected double, received unrecognized string");
                    };
                }
                return iter.readDouble();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public BigInteger readBigInteger(Schema schema) {
            try {
                return iter.readBigInteger();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public BigDecimal readBigDecimal(Schema schema) {
            try {
                return iter.readBigDecimal();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public String readString(Schema schema) {
            // TODO: this is definitely not correct, but works for testing for now. The
            // issue is that the provided strings are not wrapped with quotes.
            return new String(data, StandardCharsets.UTF_8);
        }

        @Override
        public boolean readBoolean(Schema schema) {
            try {
                return iter.readBoolean();
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public Document readDocument() {
            // TODO: What should this look like for the CLI?
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

        @Override
        public Instant readTimestamp(Schema schema) {
            try {
                // TODO: Fix time stamp resolution
                var format = timestampResolver.resolve(schema);
                var value = switch (iter.whatIsNext()) {
                    case STRING -> iter.readString();
                    case NUMBER -> iter.readDouble();
                    default -> throw new SerializationException("Expected timestamp");
                };
                return TimestampResolver.readTimestamp(value, format);
            } catch (JsonException | IOException e) {
                throw new SerializationException(e);
            }
        }

        @Override
        public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer) {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

        @Override
        public <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer) {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

        @Override
        public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer) {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

        @Override
        public boolean isNull() {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }

        @Override
        public <T> T readNull() {
            throw new UnsupportedOperationException("NOT SUPPORTED");
        }
    }
}
