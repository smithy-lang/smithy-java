/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde;

import java.io.Flushable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Serializes a shape by receiving the Smithy data model and writing output to a receiver owned by the serializer.
 *
 * <p>Note: null values should only ever be written using {@link #writeNull(Schema)}. Every other method expects
 * a non-null value or a value type.
 */
public interface ShapeSerializer extends Flushable, AutoCloseable {

    /**
     * Create a serializer that serializes nothing.
     *
     * @return the null serializer.
     */
    static ShapeSerializer nullSerializer() {
        return NullSerializer.INSTANCE;
    }

    @Override
    default void flush() {}

    @Override
    default void close() {
        flush();
    }

    /**
     * Writes a structure or union using the given member schema.
     *
     * @param schema A member schema that targets the given struct.
     * @param struct Structure to serialize.
     */
    void writeStruct(Schema schema, SerializableStruct struct);

    /**
     * Begin a list and write zero or more values into it using the provided serializer.
     *
     * @param schema    List schema.
     * @param listState State to pass into the consumer.
     * @param size      Number of elements in the list, or -1 if unknown.
     * @param consumer  Received in the context of the list and writes zero or more values.
     */
    <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer);

    /**
     * Begin a map and write zero or more entries into it using the provided serializer.
     *
     * @param schema   List schema.
     * @param mapState State to pass into the consumer.
     * @param size     Number of entries in the map, or -1 if unknown.
     * @param consumer Received in the context of the map and writes zero or more entries.
     */
    <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer);

    /**
     * Serialize a boolean.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBoolean(Schema schema, boolean value);

    /**
     * Serialize a byte.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeByte(Schema schema, byte value);

    /**
     * Serialize a short.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeShort(Schema schema, short value);

    /**
     * Serialize an integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeInteger(Schema schema, int value);

    /**
     * Serialize a long.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeLong(Schema schema, long value);

    /**
     * Serialize a float.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeFloat(Schema schema, float value);

    /**
     * Serialize a double.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeDouble(Schema schema, double value);

    /**
     * Serialize a big integer.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigInteger(Schema schema, BigInteger value);

    /**
     * Serialize a big decimal.
     *
     * @param schema Schema of the shape.
     * @param value  Value to serialize.
     */
    void writeBigDecimal(Schema schema, BigDecimal value);

    /**
     * Serialize a string.
     *
     * @param schema Schema of the shape.
     * @param value  String value.
     */
    void writeString(Schema schema, String value);

    /**
     * Serialize a blob.
     *
     * @param schema Schema of the shape.
     * @param value  Blob value.
     */
    void writeBlob(Schema schema, ByteBuffer value);

    default void writeBlob(Schema schema, byte[] value) {
        writeBlob(schema, ByteBuffer.wrap(value));
    }

    /**
     * Serialize a data stream.
     *
     * @param schema Schema of the shape.
     * @param value  Streaming value.
     */
    default void writeDataStream(Schema schema, DataStream value) {
        // by default, do nothing
    }

    /**
     * Serialize an event stream.
     *
     * @param schema Schema of the shape.
     * @param value  Event Stream value.
     */
    default void writeEventStream(Schema schema, EventStream<? extends SerializableStruct> value) {
        // by default, do nothing
    }

    /**
     * Serialize a timestamp.
     *
     * @param schema Schema of the shape.
     * @param value  Timestamp value.
     */
    void writeTimestamp(Schema schema, Instant value);

    /**
     * Serialize a document shape.
     *
     * <p>The underlying contents of the document can be serialized using {@link Document#serializeContents}.
     *
     * @param schema Schema of the shape. Generally, this should be set to {@link PreludeSchemas#DOCUMENT} unless the
     *               document wraps a modeled shape.
     * @param value  Value to serialize.
     */
    void writeDocument(Schema schema, Document value);

    /**
     * Writes a null value.
     *
     * @param schema Schema of the null value.
     */
    void writeNull(Schema schema);

    /**
     * Serializes a list of boolean values.
     */
    default void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeBoolean(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of boolean values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeBoolean(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and boolean values.
     */
    default void writeBooleanMap(Schema schema, Map<String, Boolean> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBoolean(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and boolean values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeBoolean(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of byte values.
     */
    default void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeByte(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of byte values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeByte(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and byte values.
     */
    default void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeByte(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and byte values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeByte(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of short values.
     */
    default void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeShort(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of short values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeShort(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and short values.
     */
    default void writeShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeShort(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and short values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseShortMap(Schema schema, Map<String, Short> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeShort(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of integer values.
     */
    default void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeInteger(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of integer values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeInteger(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and integer values.
     */
    default void writeIntegerMap(Schema schema, Map<String, Integer> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeInteger(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and integer values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeInteger(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of long values.
     */
    default void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeLong(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of long values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeLong(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and long values.
     */
    default void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeLong(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and long values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeLong(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of float values.
     */
    default void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeFloat(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of float values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeFloat(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and float values.
     */
    default void writeFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeFloat(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and float values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseFloatMap(Schema schema, Map<String, Float> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeFloat(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of double values.
     */
    default void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeDouble(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of double values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeDouble(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and double values.
     */
    default void writeDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeDouble(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and double values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseDoubleMap(Schema schema, Map<String, Double> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeDouble(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of big integer values.
     */
    default void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeBigInteger(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of big integer values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeBigInteger(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and big integer values.
     */
    default void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBigInteger(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and big integer values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeBigInteger(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of big decimal values.
     */
    default void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeBigDecimal(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of big decimal values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeBigDecimal(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and big decimal values.
     */
    default void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBigDecimal(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and big decimal values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeBigDecimal(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of string values.
     */
    default void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeString(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of string values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeString(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and string values.
     */
    default void writeStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeString(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and string values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseStringMap(Schema schema, Map<String, String> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeString(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of blob values.
     */
    default void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeBlob(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of blob values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeBlob(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and blob values.
     */
    default void writeBlobMap(Schema schema, Map<String, ByteBuffer> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeBlob(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and blob values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeBlob(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of timestamp values.
     */
    default void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeTimestamp(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of timestamp values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeTimestamp(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and timestamp values.
     */
    default void writeTimestampMap(Schema schema, Map<String, Instant> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeTimestamp(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and timestamp values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeTimestamp(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of document values.
     */
    default void writeDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeDocument(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of document values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseDocumentList(Schema schema, List<Document> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeDocument(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and document values.
     */
    default void writeDocumentMap(Schema schema, Map<String, Document> values, Schema keySchema, Schema valueSchema) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeDocument(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and document values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseDocumentMap(
            Schema schema,
            Map<String, Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeDocument(valueSchema, vv);
                }
            });
        }));
    }

    /**
     * Serializes a list of string enum values.
     */
    default void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeString(memberSchema, v.getValue());
            }
        });
    }

    /**
     * Serializes a list of string enum values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeString(memberSchema, v.getValue());
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and string enum values.
     */
    default void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeString(valueSchema, vv.getValue()));
        }));
    }

    /**
     * Serializes a map with string keys and string enum values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeString(valueSchema, vv.getValue());
                }
            });
        }));
    }

    /**
     * Serializes a list of int enum values.
     */
    default void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeInteger(memberSchema, v.getValue());
            }
        });
    }

    /**
     * Serializes a list of int enum values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeInteger(memberSchema, v.getValue());
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and int enum values.
     */
    default void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeInteger(valueSchema, vv.getValue()));
        }));
    }

    /**
     * Serializes a map with string keys and int enum values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeInteger(valueSchema, vv.getValue());
                }
            });
        }));
    }

    /**
     * Serializes a list of structure or union values.
     */
    default void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                s.writeStruct(memberSchema, v);
            }
        });
    }

    /**
     * Serializes a list of structure or union values. Writes null elements with {@link #writeNull(Schema)}.
     */
    default void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        writeList(schema, values, values.size(), (list, s) -> {
            for (var v : list) {
                if (v == null) {
                    s.writeNull(memberSchema);
                } else {
                    s.writeStruct(memberSchema, v);
                }
            }
        });
    }

    /**
     * Serializes a map with string keys and structure or union values.
     */
    default void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> s.writeStruct(valueSchema, vv));
        }));
    }

    /**
     * Serializes a map with string keys and structure or union values. Writes null values with {@link #writeNull(Schema)}.
     */
    default void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        writeMap(schema, values, values.size(), (map, ms) -> map.forEach((k, v) -> {
            ms.writeEntry(keySchema, k, v, (vv, s) -> {
                if (vv == null) {
                    s.writeNull(valueSchema);
                } else {
                    s.writeStruct(valueSchema, vv);
                }
            });
        }));
    }
}
