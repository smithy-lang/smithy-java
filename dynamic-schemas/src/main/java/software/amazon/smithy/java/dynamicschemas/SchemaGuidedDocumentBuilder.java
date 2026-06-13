/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Allows a StructDocument to be used in a ShapeBuilder.
 */
final class SchemaGuidedDocumentBuilder implements ShapeBuilder<StructDocument> {

    private static final int DEFAULT_COLLECTION_CAPACITY = 16;

    private final ShapeId service;
    private final Schema target;
    private final Document[] values;
    private final StructConsumer structConsumer = new StructConsumer();
    private final UnionConsumer unionConsumer = new UnionConsumer();
    private final ListConsumer listConsumer = new ListConsumer();
    private final MapConsumer mapConsumer = new MapConsumer();

    SchemaGuidedDocumentBuilder(Schema target, ShapeId service) {
        if (target.type() != ShapeType.STRUCTURE && target.type() != ShapeType.UNION) {
            throw new IllegalArgumentException("StructDocument can only deserialize a structure or union, "
                    + "but got " + target);
        }

        this.target = target;
        this.service = service;
        this.values = new Document[target.members().size()];
    }

    @Override
    public Schema schema() {
        return target;
    }

    @Override
    public StructDocument build() {
        if (target.type() == ShapeType.UNION) {
            boolean hasValue = false;
            for (Document v : values) {
                if (v != null) {
                    hasValue = true;
                    break;
                }
            }
            if (!hasValue) {
                throw new IllegalArgumentException("No value set for union document: " + schema().id());
            }
        }
        return new StructDocument(target, values, service);
    }

    @Override
    public void setMemberValue(Schema member, Object value) {
        SchemaUtils.validateMemberInSchema(target, member, value);

        Document convertedValue = switch (value) {
            case Document d -> StructDocument.convertDocument(member, d, service);
            case DataStream ds -> Document.of(member, ds);
            case EventStream<?> es -> Document.of(member, es);
            case null, default -> StructDocument.convertDocument(member, Document.ofObject(value), service);
        };

        values[member.memberIndex()] = convertedValue;
    }

    @Override
    public ShapeBuilder<StructDocument> deserialize(ShapeDeserializer decoder) {
        populateFromDecoder(decoder, target);
        return this;
    }

    @Override
    public ShapeBuilder<StructDocument> deserializeMember(ShapeDeserializer decoder, Schema schema) {
        populateFromDecoder(decoder, schema.assertMemberTargetIs(target));
        return this;
    }

    private void populateFromDecoder(ShapeDeserializer decoder, Schema schema) {
        decoder.readStruct(schema, values, structConsumer);
    }

    private Document deserializeValue(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case STRING, ENUM -> new ContentDocument(Document.of(decoder.readString(schema)), schema);
            case BOOLEAN -> new ContentDocument(Document.of(decoder.readBoolean(schema)), schema);
            case INTEGER, INT_ENUM -> new ContentDocument(Document.ofNumber(decoder.readInteger(schema)), schema);
            case LONG -> new ContentDocument(Document.ofNumber(decoder.readLong(schema)), schema);
            case DOUBLE -> new ContentDocument(Document.ofNumber(decoder.readDouble(schema)), schema);
            case FLOAT -> new ContentDocument(Document.ofNumber(decoder.readFloat(schema)), schema);
            case BYTE -> new ContentDocument(Document.ofNumber(decoder.readByte(schema)), schema);
            case SHORT -> new ContentDocument(Document.ofNumber(decoder.readShort(schema)), schema);
            case TIMESTAMP -> new ContentDocument(Document.of(decoder.readTimestamp(schema)), schema);
            case BIG_DECIMAL -> new ContentDocument(Document.ofNumber(decoder.readBigDecimal(schema)), schema);
            case BIG_INTEGER -> new ContentDocument(Document.ofNumber(decoder.readBigInteger(schema)), schema);
            case DOCUMENT -> new ContentDocument(decoder.readDocument(), schema);
            case BLOB -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, decoder.readDataStream(schema));
                }
                yield new ContentDocument(Document.of(decoder.readBlob(schema)), schema);
            }
            default -> deserializeAggregate(decoder, schema);
        };
    }

    private Document deserializeAggregate(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case LIST -> deserializeList(decoder, schema);
            case MAP -> deserializeMap(decoder, schema);
            case STRUCTURE -> createStructDocument(decoder, schema);
            case UNION -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, decoder.readEventStream(schema));
                }
                yield createStructDocument(decoder, schema);
            }
            default -> throw new UnsupportedOperationException("Unsupported target type: " + schema.type());
        };
    }

    private Document deserializeList(ShapeDeserializer decoder, Schema schema) {
        listConsumer.schema = schema.listMember();
        listConsumer.sparse = schema.hasTrait(TraitKey.SPARSE_TRAIT);
        int size = decoder.containerSize();
        var items = size >= 0 && size <= decoder.containerPreAllocationLimit()
                ? new ArrayList<Document>(size)
                : new ArrayList<Document>(DEFAULT_COLLECTION_CAPACITY);
        decoder.readList(schema, items, listConsumer);
        return new ContentDocument(Document.of(items), schema);
    }

    private Document deserializeMap(ShapeDeserializer decoder, Schema schema) {
        mapConsumer.schema = schema.mapValueMember();
        mapConsumer.sparse = schema.hasTrait(TraitKey.SPARSE_TRAIT);
        int size = decoder.containerSize();
        var map = size >= 0 && size <= decoder.containerPreAllocationLimit()
                ? new HashMap<String, Document>(size, 1.0f)
                : new HashMap<String, Document>(DEFAULT_COLLECTION_CAPACITY);
        decoder.readStringMap(schema, map, mapConsumer);
        return new ContentDocument(Document.of(map), schema);
    }

    private StructDocument createStructDocument(ShapeDeserializer decoder, Schema schema) {
        if (schema.type() == ShapeType.UNION) {
            return createUnionDocument(decoder, schema);
        }
        Document[] nestedValues = new Document[schema.members().size()];
        decoder.readStruct(schema, nestedValues, structConsumer);
        return new StructDocument(schema, nestedValues, service);
    }

    private StructDocument createUnionDocument(ShapeDeserializer decoder, Schema schema) {
        unionConsumer.value = null;
        unionConsumer.memberIndex = -1;
        decoder.readStruct(schema, null, unionConsumer);
        if (unionConsumer.memberIndex >= 0) {
            return new StructDocument(schema, unionConsumer.value, unionConsumer.memberIndex, service);
        }
        return new StructDocument(schema, new Document[schema.members().size()], service);
    }

    @Override
    public ShapeBuilder<StructDocument> errorCorrection() {
        // TODO: fill in defaults.
        return this;
    }

    private final class StructConsumer implements ShapeDeserializer.StructMemberConsumer<Document[]> {
        @Override
        public void accept(Document[] state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
            state[memberSchema.memberIndex()] = deserializeValue(memberDeserializer, memberSchema);
        }
    }

    private final class UnionConsumer implements ShapeDeserializer.StructMemberConsumer<Object> {
        Document value;
        int memberIndex;

        @Override
        public void accept(Object state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
            value = deserializeValue(memberDeserializer, memberSchema);
            memberIndex = memberSchema.memberIndex();
        }
    }

    private final class ListConsumer implements ShapeDeserializer.ListMemberConsumer<List<Document>> {
        Schema schema;
        boolean sparse;

        @Override
        public void accept(List<Document> state, ShapeDeserializer memberDeserializer) {
            if (sparse && memberDeserializer.isNull()) {
                state.add(memberDeserializer.readNull());
            } else {
                state.add(deserializeValue(memberDeserializer, schema));
            }
        }
    }

    private final class MapConsumer implements ShapeDeserializer.MapMemberConsumer<String, Map<String, Document>> {
        Schema schema;
        boolean sparse;

        @Override
        public void accept(Map<String, Document> state, String key, ShapeDeserializer memberDeserializer) {
            if (sparse && memberDeserializer.isNull()) {
                state.put(key, memberDeserializer.readNull());
            } else {
                state.put(key, deserializeValue(memberDeserializer, schema));
            }
        }
    }
}
