/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Form-urlencoded serializer for both {@code awsQuery} and {@code ec2Query} protocols.
 *
 * <p>The two protocols share the same wire format but differ in member name resolution,
 * list serialization, and map support. The {@link QueryVariant} flag controls these differences.
 */
final class QueryFormSerializer implements ShapeSerializer {

    /**
     * Selects the protocol-specific serialization behavior.
     */
    enum QueryVariant {
        /** Standard AWS Query protocol. */
        AWS_QUERY,
        /** EC2 Query protocol. */
        EC2_QUERY
    }

    private static final byte[] ACTION_PREFIX = "Action=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VERSION_PREFIX = "&Version=".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MEMBER = "member".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENTRY = "entry".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEY = "key".getBytes(StandardCharsets.UTF_8);
    private static final byte[] VALUE = "value".getBytes(StandardCharsets.UTF_8);

    private final FormUrlEncodedSink sink;
    private final QueryVariant variant;

    private byte[][] prefixCache = new byte[8][];
    private int prefixDepth = 0;

    private final ListItemSerializer listSerializer = new ListItemSerializer();
    private final QueryMapSerializer mapSerializer = new QueryMapSerializer();

    QueryFormSerializer(QueryVariant variant, String action, String version) {
        this.variant = variant;
        this.sink = new FormUrlEncodedSink();
        sink.writeBytes(ACTION_PREFIX, 0, ACTION_PREFIX.length);
        sink.writeAscii(action);
        sink.writeBytes(VERSION_PREFIX, 0, VERSION_PREFIX.length);
        sink.writeAscii(version);
    }

    ByteBuffer finish() {
        return sink.finish();
    }

    private void writeParam(byte[] key, String value) {
        sink.writeByte('&');
        writeCurrentPrefix();
        if (prefixDepth > 0) {
            sink.writeByte('.');
        }
        sink.writeBytes(key, 0, key.length);
        sink.writeByte('=');
        sink.writeUrlEncoded(value);
    }

    private void writeCurrentPrefix() {
        for (int i = 0; i < prefixDepth; i++) {
            if (i > 0) {
                sink.writeByte('.');
            }
            sink.writeBytes(prefixCache[i], 0, prefixCache[i].length);
        }
    }

    private void pushPrefix(byte[] prefix) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = prefix;
    }

    private void pushIndexedPrefix(byte[] base, int index) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = encodeIndexedPrefix(base, index);
    }

    private void pushIndexPrefix(int index) {
        ensurePrefixCacheCapacity();
        prefixCache[prefixDepth++] = encodeIndex(index);
    }

    private void ensurePrefixCacheCapacity() {
        if (prefixDepth >= prefixCache.length) {
            prefixCache = Arrays.copyOf(prefixCache, prefixCache.length * 2);
        }
    }

    private void popPrefix() {
        prefixDepth--;
    }

    @SuppressWarnings("deprecation")
    private byte[] encodeIndexedPrefix(byte[] base, int index) {
        String indexStr = Integer.toString(index);
        byte[] result = new byte[base.length + 1 + indexStr.length()];
        System.arraycopy(base, 0, result, 0, base.length);
        result[base.length] = '.';
        indexStr.getBytes(0, indexStr.length(), result, base.length + 1);
        return result;
    }

    @SuppressWarnings("deprecation")
    private byte[] encodeIndex(int index) {
        String indexStr = Integer.toString(index);
        byte[] result = new byte[indexStr.length()];
        indexStr.getBytes(0, indexStr.length(), result, 0);
        return result;
    }

    // --- Member name resolution (protocol-specific) ---

    /**
     * Read the pre-computed URL-encoded member-name bytes from the schema extension.
     */
    private byte[] getMemberNameBytes(Schema schema) {
        var ext = schema.getExtension(AwsQuerySchemaExtensions.KEY);
        if (ext == null) {
            return null;
        }
        return variant == QueryVariant.AWS_QUERY ? ext.awsQueryNameBytes() : ext.ec2QueryNameBytes();
    }

    // --- Struct ---

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        if (schema.isMember()) {
            // Member schemas always have a non-null QueryMemberBinding (see provider).
            pushPrefix(getMemberNameBytes(schema));
            struct.serializeMembers(this);
            popPrefix();
        } else {
            struct.serializeMembers(this);
        }
    }

    // --- List (protocol-specific) ---

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        if (variant == QueryVariant.EC2_QUERY) {
            writeEc2List(schema, listState, size, consumer);
        } else {
            writeAwsQueryList(schema, listState, size, consumer);
        }
    }

    private <T> void writeAwsQueryList(
            Schema schema,
            T listState,
            int size,
            BiConsumer<T, ShapeSerializer> consumer
    ) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema memberSchema = schema.listMember();

        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        if (size == 0) {
            writeEmptyValue();
            if (schema.isMember()) {
                popPrefix();
            }
            return;
        }

        byte[] memberNameBytes;
        if (flattened) {
            memberNameBytes = null;
        } else {
            var xmlName = memberSchema.getTrait(TraitKey.XML_NAME_TRAIT);
            memberNameBytes = xmlName != null ? xmlName.getValue().getBytes(StandardCharsets.UTF_8) : MEMBER;
        }

        listSerializer.reset(memberNameBytes, flattened);
        consumer.accept(listState, listSerializer);

        if (schema.isMember()) {
            popPrefix();
        }
    }

    private <T> void writeEc2List(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        // EC2 Query lists are always flattened - no .member. segment
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        if (size == 0) {
            if (schema.isMember()) {
                popPrefix();
            }
            return;
        }

        listSerializer.reset(null, true);
        consumer.accept(listState, listSerializer);

        if (schema.isMember()) {
            popPrefix();
        }
    }

    private void beginList(Schema schema) {
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }
    }

    private void endList(Schema schema) {
        if (schema.isMember()) {
            popPrefix();
        }
    }

    private void setupListSerializer(Schema schema) {
        boolean flattened;
        byte[] memberNameBytes;
        if (variant == QueryVariant.EC2_QUERY) {
            flattened = true;
            memberNameBytes = null;
        } else {
            flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            if (flattened) {
                memberNameBytes = null;
            } else {
                Schema memberSchema = schema.listMember();
                var xmlName = memberSchema.getTrait(TraitKey.XML_NAME_TRAIT);
                memberNameBytes = xmlName != null ? xmlName.getValue().getBytes(StandardCharsets.UTF_8) : MEMBER;
            }
        }
        listSerializer.reset(memberNameBytes, flattened);
    }

    @Override
    public void writeStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeStruct(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeStruct(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeString(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeString(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeBoolean(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeBoolean(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeByte(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeByte(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeShort(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeShort(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeInteger(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeLong(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeLong(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeFloat(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeFloat(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeDouble(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeDouble(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeBigInteger(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeBigInteger(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeBigDecimal(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeBigDecimal(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeBlob(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeBlob(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeTimestamp(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeTimestamp(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeString(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                listSerializer.writeString(memberSchema, v.getValue());
            }
        }
        endList(schema);
    }

    @Override
    public void writeIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeInteger(memberSchema, values.get(i).getValue());
            }
        } else {
            for (var v : values) {
                listSerializer.writeInteger(memberSchema, v.getValue());
            }
        }
        endList(schema);
    }

    // --- Sparse list methods ---

    @Override
    public void writeSparseStructList(Schema schema, List<? extends SerializableStruct> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeStruct(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeStruct(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseStringList(Schema schema, List<String> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseIntegerList(Schema schema, List<Integer> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseLongList(Schema schema, List<Long> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeLong(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeLong(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseDoubleList(Schema schema, List<Double> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDouble(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDouble(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBlobList(Schema schema, List<ByteBuffer> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBlob(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBlob(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseTimestampList(Schema schema, List<Instant> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeTimestamp(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeTimestamp(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBooleanList(Schema schema, List<Boolean> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBoolean(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBoolean(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseByteList(Schema schema, List<Byte> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeByte(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeByte(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseShortList(Schema schema, List<Short> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeShort(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeShort(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseFloatList(Schema schema, List<Float> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeFloat(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeFloat(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBigIntegerList(Schema schema, List<BigInteger> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigInteger(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigInteger(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseBigDecimalList(Schema schema, List<BigDecimal> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeBigDecimal(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseEnumList(Schema schema, List<? extends SmithyEnum> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeString(memberSchema, v.getValue());
                }
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseIntEnumList(Schema schema, List<? extends SmithyIntEnum> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeInteger(memberSchema, v.getValue());
                }
            }
        }
        endList(schema);
    }

    // --- Specialized non-sparse list methods that are not in the default interface ---

    @Override
    public void writeDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                listSerializer.writeDocument(memberSchema, values.get(i));
            }
        } else {
            for (var v : values) {
                listSerializer.writeDocument(memberSchema, v);
            }
        }
        endList(schema);
    }

    @Override
    public void writeSparseDocumentList(Schema schema, List<? extends Document> values, Schema memberSchema) {
        beginList(schema);
        if (values.isEmpty()) {
            if (variant != QueryVariant.EC2_QUERY) {
                writeEmptyValue();
            }
            endList(schema);
            return;
        }
        setupListSerializer(schema);
        if (values instanceof RandomAccess) {
            for (int i = 0, sz = values.size(); i < sz; i++) {
                var v = values.get(i);
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDocument(memberSchema, v);
                }
            }
        } else {
            for (var v : values) {
                if (v == null) {
                    listSerializer.writeNull(memberSchema);
                } else {
                    listSerializer.writeDocument(memberSchema, v);
                }
            }
        }
        endList(schema);
    }

    private void writeEmptyValue() {
        sink.writeByte('&');
        writeCurrentPrefix();
        sink.writeByte('=');
    }

    private final class ListItemSerializer implements ShapeSerializer {
        private byte[] memberNameBytes;
        private boolean flattened;
        private int index;

        void reset(byte[] memberNameBytes, boolean flattened) {
            this.memberNameBytes = memberNameBytes;
            this.flattened = flattened;
            this.index = 1;
        }

        private void pushIndexedMemberPrefix() {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushIndexedPrefix(memberNameBytes, index);
            }
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            pushIndexedMemberPrefix();
            index++;
            struct.serializeMembers(QueryFormSerializer.this);
            popPrefix();
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeList(schema, listState, size, consumer);
            popPrefix();
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            pushIndexedMemberPrefix();
            index++;
            QueryFormSerializer.this.writeMap(schema, mapState, size, consumer);
            popPrefix();
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeIndexedParam(value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeIndexedParam(Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeIndexedParam(Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeIndexedParam(Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeIndexedParam(Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (Float.isNaN(value)) {
                writeIndexedParam("NaN");
            } else if (Float.isInfinite(value)) {
                writeIndexedParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeIndexedParam(Float.toString(value));
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (Double.isNaN(value)) {
                writeIndexedParam("NaN");
            } else if (Double.isInfinite(value)) {
                writeIndexedParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeIndexedParam(Double.toString(value));
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeIndexedParam(value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeIndexedParam(value.toPlainString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeIndexedParam(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeIndexedParam(ByteBufferUtils.base64Encode(value));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
            writeIndexedParam(formatter.writeString(value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("Query protocols do not support document types");
        }

        @Override
        public void writeNull(Schema schema) {
            index++;
        }

        private void writeIndexedParam(String value) {
            sink.writeByte('&');
            writeCurrentPrefix();
            if (prefixDepth > 0) {
                sink.writeByte('.');
            }
            if (flattened) {
                sink.writeInt(index);
            } else {
                sink.writeBytes(memberNameBytes, 0, memberNameBytes.length);
                sink.writeByte('.');
                sink.writeInt(index);
            }
            sink.writeByte('=');
            sink.writeUrlEncoded(value);
            index++;
        }
    }

    // --- Map (awsQuery only) ---

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        if (variant == QueryVariant.EC2_QUERY) {
            throw new SerializationException("EC2 Query protocol does not support map serialization");
        }

        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema keySchema = schema.mapKeyMember();
        Schema valueSchema = schema.mapValueMember();

        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }

        var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
        var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

        byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
        byte[] valueNameBytes = valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
        byte[] entryNameBytes = flattened ? null : ENTRY;

        mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
        consumer.accept(mapState, mapSerializer);

        if (schema.isMember()) {
            popPrefix();
        }
    }

    // --- Specialized map methods ---

    private void beginMap(Schema schema) {
        if (variant == QueryVariant.EC2_QUERY) {
            throw new SerializationException("EC2 Query protocol does not support map serialization");
        }
        if (schema.isMember()) {
            pushPrefix(getMemberNameBytes(schema));
        }
    }

    private void endMap(Schema schema) {
        if (schema.isMember()) {
            popPrefix();
        }
    }

    private void setupMapSerializer(Schema schema) {
        boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
        Schema keySchema = schema.mapKeyMember();
        Schema valueSchema = schema.mapValueMember();

        var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
        var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

        byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
        byte[] valueNameBytes = valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
        byte[] entryNameBytes = flattened ? null : ENTRY;

        mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
    }

    @Override
    public void writeStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeStructEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeBooleanEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeByteMap(Schema schema, Map<String, Byte> values, Schema keySchema, Schema valueSchema) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeByteEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeShortEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeLongMap(Schema schema, Map<String, Long> values, Schema keySchema, Schema valueSchema) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeLongEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeFloatEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeDoubleEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeBigIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeBigDecimalEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeBlobEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeTimestampEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeDocumentEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
        }
        endMap(schema);
    }

    @Override
    public void writeIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            mapSerializer.writeIntEnumEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
        }
        endMap(schema);
    }

    // --- Sparse map methods ---

    @Override
    public void writeSparseStructMap(
            Schema schema,
            Map<String, ? extends SerializableStruct> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeStructEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseStringMap(
            Schema schema,
            Map<String, String> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseIntegerMap(
            Schema schema,
            Map<String, Integer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseLongMap(
            Schema schema,
            Map<String, Long> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeLongEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseDoubleMap(
            Schema schema,
            Map<String, Double> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeDoubleEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseBlobMap(
            Schema schema,
            Map<String, ByteBuffer> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeBlobEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseTimestampMap(
            Schema schema,
            Map<String, Instant> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeTimestampEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseDocumentMap(
            Schema schema,
            Map<String, ? extends Document> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeDocumentEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseBooleanMap(
            Schema schema,
            Map<String, Boolean> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeBooleanEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseByteMap(
            Schema schema,
            Map<String, Byte> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeByteEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseShortMap(
            Schema schema,
            Map<String, Short> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeShortEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseFloatMap(
            Schema schema,
            Map<String, Float> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeFloatEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseBigIntegerMap(
            Schema schema,
            Map<String, BigInteger> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeBigIntegerEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseBigDecimalMap(
            Schema schema,
            Map<String, BigDecimal> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeBigDecimalEntry(keySchema, entry.getKey(), valueSchema, entry.getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseEnumMap(
            Schema schema,
            Map<String, ? extends SmithyEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeStringEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
            }
        }
        endMap(schema);
    }

    @Override
    public void writeSparseIntEnumMap(
            Schema schema,
            Map<String, ? extends SmithyIntEnum> values,
            Schema keySchema,
            Schema valueSchema
    ) {
        beginMap(schema);
        setupMapSerializer(schema);
        for (var entry : values.entrySet()) {
            if (entry.getValue() == null) {
                mapSerializer.writeNullEntry(keySchema, entry.getKey(), valueSchema);
            } else {
                mapSerializer.writeIntEnumEntry(keySchema, entry.getKey(), valueSchema, entry.getValue().getValue());
            }
        }
        endMap(schema);
    }

    private final class QueryMapSerializer implements MapSerializer {
        private byte[] entryNameBytes;
        private byte[] keyNameBytes;
        private byte[] valueNameBytes;
        private boolean flattened;
        private int index;

        void reset(byte[] entryNameBytes, byte[] keyNameBytes, byte[] valueNameBytes, boolean flattened) {
            this.entryNameBytes = entryNameBytes;
            this.keyNameBytes = keyNameBytes;
            this.valueNameBytes = valueNameBytes;
            this.flattened = flattened;
            this.index = 1;
        }

        @Override
        public <T> void writeEntry(
                Schema keySchema,
                String key,
                T state,
                BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushIndexedPrefix(entryNameBytes, index);
            }

            writeParam(keyNameBytes, key);

            pushPrefix(valueNameBytes);
            valueSerializer.accept(state, mapValueSerializer);
            popPrefix();

            popPrefix();
            index++;
        }

        @Override
        public void writeStructEntry(Schema keySchema, String key, Schema valueSchema, SerializableStruct value) {
            beginEntry(key);
            mapValueSerializer.writeStruct(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeStringEntry(Schema keySchema, String key, Schema valueSchema, String value) {
            beginEntry(key);
            mapValueSerializer.writeString(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeBooleanEntry(Schema keySchema, String key, Schema valueSchema, Boolean value) {
            beginEntry(key);
            mapValueSerializer.writeBoolean(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeByteEntry(Schema keySchema, String key, Schema valueSchema, Byte value) {
            beginEntry(key);
            mapValueSerializer.writeByte(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeShortEntry(Schema keySchema, String key, Schema valueSchema, Short value) {
            beginEntry(key);
            mapValueSerializer.writeShort(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeIntegerEntry(Schema keySchema, String key, Schema valueSchema, Integer value) {
            beginEntry(key);
            mapValueSerializer.writeInteger(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeLongEntry(Schema keySchema, String key, Schema valueSchema, Long value) {
            beginEntry(key);
            mapValueSerializer.writeLong(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeFloatEntry(Schema keySchema, String key, Schema valueSchema, Float value) {
            beginEntry(key);
            mapValueSerializer.writeFloat(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeDoubleEntry(Schema keySchema, String key, Schema valueSchema, Double value) {
            beginEntry(key);
            mapValueSerializer.writeDouble(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeBigIntegerEntry(Schema keySchema, String key, Schema valueSchema, BigInteger value) {
            beginEntry(key);
            mapValueSerializer.writeBigInteger(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeBigDecimalEntry(Schema keySchema, String key, Schema valueSchema, BigDecimal value) {
            beginEntry(key);
            mapValueSerializer.writeBigDecimal(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeBlobEntry(Schema keySchema, String key, Schema valueSchema, ByteBuffer value) {
            beginEntry(key);
            mapValueSerializer.writeBlob(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeTimestampEntry(Schema keySchema, String key, Schema valueSchema, Instant value) {
            beginEntry(key);
            mapValueSerializer.writeTimestamp(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeDocumentEntry(Schema keySchema, String key, Schema valueSchema, Document value) {
            beginEntry(key);
            mapValueSerializer.writeDocument(valueSchema, value);
            endEntry();
        }

        @Override
        public void writeNullEntry(Schema keySchema, String key, Schema valueSchema) {
            beginEntry(key);
            mapValueSerializer.writeNull(valueSchema);
            endEntry();
        }

        @Override
        public void writeIntEnumEntry(Schema keySchema, String key, Schema valueSchema, int value) {
            beginEntry(key);
            mapValueSerializer.writeInteger(valueSchema, value);
            endEntry();
        }

        private void beginEntry(String key) {
            if (flattened) {
                pushIndexPrefix(index);
            } else {
                pushIndexedPrefix(entryNameBytes, index);
            }
            writeParam(keyNameBytes, key);
            pushPrefix(valueNameBytes);
        }

        private void endEntry() {
            popPrefix();
            popPrefix();
            index++;
        }
    }

    private final MapValueSerializer mapValueSerializer = new MapValueSerializer();

    private final class MapValueSerializer implements ShapeSerializer {
        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            struct.serializeMembers(QueryFormSerializer.this);
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            Schema memberSchema = schema.listMember();

            if (size == 0) {
                writeEmptyValue();
                return;
            }

            byte[] memberNameBytes;
            if (flattened) {
                memberNameBytes = null;
            } else {
                var xmlName = memberSchema.getTrait(TraitKey.XML_NAME_TRAIT);
                memberNameBytes = xmlName != null ? xmlName.getValue().getBytes(StandardCharsets.UTF_8) : MEMBER;
            }

            listSerializer.reset(memberNameBytes, flattened);
            consumer.accept(listState, listSerializer);
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            boolean flattened = schema.hasTrait(TraitKey.XML_FLATTENED_TRAIT);
            Schema keySchema = schema.mapKeyMember();
            Schema valueSchema = schema.mapValueMember();

            var keyXmlName = keySchema.getTrait(TraitKey.XML_NAME_TRAIT);
            var valueXmlName = valueSchema.getTrait(TraitKey.XML_NAME_TRAIT);

            byte[] keyNameBytes = keyXmlName != null ? keyXmlName.getValue().getBytes(StandardCharsets.UTF_8) : KEY;
            byte[] valueNameBytes =
                    valueXmlName != null ? valueXmlName.getValue().getBytes(StandardCharsets.UTF_8) : VALUE;
            byte[] entryNameBytes = flattened ? null : ENTRY;

            mapSerializer.reset(entryNameBytes, keyNameBytes, valueNameBytes, flattened);
            consumer.accept(mapState, mapSerializer);
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            writeValueParam(value ? "true" : "false");
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            writeValueParam(Byte.toString(value));
        }

        @Override
        public void writeShort(Schema schema, short value) {
            writeValueParam(Short.toString(value));
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            writeValueParam(Integer.toString(value));
        }

        @Override
        public void writeLong(Schema schema, long value) {
            writeValueParam(Long.toString(value));
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            if (Float.isNaN(value)) {
                writeValueParam("NaN");
            } else if (Float.isInfinite(value)) {
                writeValueParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeValueParam(Float.toString(value));
            }
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            if (Double.isNaN(value)) {
                writeValueParam("NaN");
            } else if (Double.isInfinite(value)) {
                writeValueParam(value > 0 ? "Infinity" : "-Infinity");
            } else {
                writeValueParam(Double.toString(value));
            }
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            writeValueParam(value.toString());
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            writeValueParam(value.toPlainString());
        }

        @Override
        public void writeString(Schema schema, String value) {
            writeValueParam(value);
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            writeValueParam(ByteBufferUtils.base64Encode(value));
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
            writeValueParam(formatter.writeString(value));
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            throw new SerializationException("Query protocols do not support document types");
        }

        @Override
        public void writeNull(Schema schema) {}

        private void writeValueParam(String value) {
            sink.writeByte('&');
            writeCurrentPrefix();
            sink.writeByte('=');
            sink.writeUrlEncoded(value);
        }
    }

    // --- Scalar writes ---

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        writeParam(getMemberNameBytes(schema), value ? "true" : "false");
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        writeParam(getMemberNameBytes(schema), Byte.toString(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        writeParam(getMemberNameBytes(schema), Short.toString(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        writeParam(getMemberNameBytes(schema), Integer.toString(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        writeParam(getMemberNameBytes(schema), Long.toString(value));
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        byte[] memberNameBytes = getMemberNameBytes(schema);
        if (Float.isNaN(value)) {
            writeParam(memberNameBytes, "NaN");
        } else if (Float.isInfinite(value)) {
            writeParam(memberNameBytes, value > 0 ? "Infinity" : "-Infinity");
        } else {
            writeParam(memberNameBytes, Float.toString(value));
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        byte[] memberNameBytes = getMemberNameBytes(schema);
        if (Double.isNaN(value)) {
            writeParam(memberNameBytes, "NaN");
        } else if (Double.isInfinite(value)) {
            writeParam(memberNameBytes, value > 0 ? "Infinity" : "-Infinity");
        } else {
            writeParam(memberNameBytes, Double.toString(value));
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        writeParam(getMemberNameBytes(schema), value.toString());
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        writeParam(getMemberNameBytes(schema), value.toPlainString());
    }

    @Override
    public void writeString(Schema schema, String value) {
        writeParam(getMemberNameBytes(schema), value);
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        writeParam(getMemberNameBytes(schema), ByteBufferUtils.base64Encode(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        TimestampFormatter formatter = TimestampFormatter.of(schema, TimestampFormatTrait.Format.DATE_TIME);
        writeParam(getMemberNameBytes(schema), formatter.writeString(value));
    }

    @Override
    public void writeDocument(Schema schema, Document value) {
        throw new SerializationException("Query protocols do not support document types");
    }

    @Override
    public void writeNull(Schema schema) {}
}
