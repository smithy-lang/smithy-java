/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonCodec;
import software.amazon.smithy.java.runtime.json.JsonDocuments;
import software.amazon.smithy.java.runtime.json.JsonFieldMapper;
import software.amazon.smithy.java.runtime.json.TimestampResolver;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonException;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonIterator;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.Slice;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.ValueType;
import software.amazon.smithy.model.shapes.ShapeType;

final class JsonIterDeserializer implements ShapeDeserializer {

    private JsonIterator iter;
    private final JsonCodec.Settings settings;
    private final Consumer<JsonIterator> returnHandle;

    JsonIterDeserializer(JsonIterator iter, JsonCodec.Settings settings, Consumer<JsonIterator> returnHandle) {
        this.iter = iter;
        this.settings = settings;
        this.returnHandle = returnHandle;
    }

    @Override
    public void close() {
        try {
            iter.close();
            returnHandle.accept(iter);
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        } finally {
            iter = null;
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
        try {
            return iter.readString();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
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
        try {
            return switch (iter.whatIsNext()) {
                case STRING -> JsonDocuments.createString(iter.readString(), settings);
                case BOOLEAN -> JsonDocuments.createBoolean(iter.readBoolean(), settings);
                case NULL -> {
                    iter.readNull();
                    yield null;
                }
                case NUMBER -> JsonDocuments.createNumber(iter.readNumber(), settings);
                case ARRAY -> {
                    List<Document> elements = new ArrayList<>();
                    if (iter.startReadArray()) {
                        do {
                            elements.add(readDocument());
                        } while (iter.readNextArrayValue());
                    }
                    yield JsonDocuments.createList(elements, settings);
                }
                case OBJECT -> {
                    Map<String, Document> elements = new LinkedHashMap<>();
                    if (iter.startReadObject()) {
                        do {
                            var field = iter.readObjectKey();
                            var value = readDocument();
                            elements.put(field, value);
                        } while (iter.keepReadingObject());
                    }
                    yield JsonDocuments.createMap(elements, settings);
                }
                case INVALID -> throw new SerializationException("Expected JSON value, found: " + iter.whatIsNext());
            };
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public Instant readTimestamp(Schema schema) {
        try {
            var format = settings.timestampResolver().resolve(schema);
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
    public <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> structMemberConsumer) {
        try {
            if (settings.fieldMapper() == JsonFieldMapper.UseMemberName.INSTANCE) {
                readObjectSlices(schema, state, structMemberConsumer);
            } else {
                readObjectStrings(schema, state, structMemberConsumer);
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    private <T> void readObjectSlices(
        Schema schema,
        T state,
        StructMemberConsumer<T> structMemberConsumer
    ) throws IOException {
        if (iter.startReadObject()) {
            do {
                Slice field;
                try {
                    field = iter.readObjectKeySlice();
                } catch (JsonException e) {
                    // Slices don't support "\", so handle those invalid cases.
                    readObjectFieldAsString(schema, state, structMemberConsumer);
                    continue;
                }

                var member = schema.findMember(field.data(), field.head(), field.tail());
                if (member != null) {
                    structMemberConsumer.accept(state, member, this);
                } else if (schema.type() == ShapeType.UNION && settings.forbidUnknownUnionMembers()) {
                    throw new SerializationException("Unknown member " + field + " encountered");
                } else {
                    structMemberConsumer.unknownMember(state, field.toString());
                    iter.skip();
                }
            } while (iter.keepReadingObject());
        }
    }

    private <T> void readObjectStrings(
        Schema schema,
        T state,
        StructMemberConsumer<T> structMemberConsumer
    ) throws IOException {
        if (iter.startReadObject()) {
            do {
                readObjectFieldAsString(schema, state, structMemberConsumer);
            } while (iter.keepReadingObject());
        }
    }

    private <T> void readObjectFieldAsString(
        Schema schema,
        T state,
        StructMemberConsumer<T> structMemberConsumer
    ) throws IOException {
        var field = iter.readObjectKey();
        var member = settings.fieldMapper().fieldToMember(schema, field);
        if (member != null) {
            structMemberConsumer.accept(state, member, this);
        } else if (schema.type() == ShapeType.UNION && settings.forbidUnknownUnionMembers()) {
            throw new SerializationException("Unknown member " + field + " encountered");
        } else {
            structMemberConsumer.unknownMember(state, field);
            iter.skip();
        }
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        try {
            if (iter.startReadArray()) {
                do {
                    listMemberConsumer.accept(state, this);
                } while (iter.readNextArrayValue());
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> mapMemberConsumer) {
        try {
            if (iter.startReadObject()) {
                do {
                    var field = iter.readObjectKey();
                    mapMemberConsumer.accept(state, field, this);
                } while (iter.keepReadingObject());
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public boolean isNull() {
        try {
            return iter.whatIsNext() == ValueType.NULL;
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> T readNull() {
        try {
            iter.readNull();
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
        return null;
    }
}
