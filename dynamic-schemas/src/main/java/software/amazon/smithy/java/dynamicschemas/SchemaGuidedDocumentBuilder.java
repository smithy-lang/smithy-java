/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
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
        return new StructDocument(target, values);
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

    // Common scalars inline here; rare scalars and aggregates split out to stay under C2's FreqInlineSize.
    private Document deserializeValue(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case STRING, ENUM -> new ContentDocument(schema, decoder.readString(schema));
            case BOOLEAN -> new ContentDocument(schema, decoder.readBoolean(schema));
            case INTEGER, INT_ENUM -> new ContentDocument(schema, decoder.readInteger(schema));
            case LONG -> new ContentDocument(schema, decoder.readLong(schema));
            case DOUBLE -> new ContentDocument(schema, decoder.readDouble(schema));
            case LIST, MAP, STRUCTURE, UNION -> deserializeAggregate(decoder, schema);
            default -> deserializeRareScalar(decoder, schema);
        };
    }

    // Union variant: returns raw boxed scalars (no ContentDocument wrapper) to avoid per-union allocation.
    private Object deserializeUnionValue(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case STRING, ENUM -> decoder.readString(schema);
            case BOOLEAN -> decoder.readBoolean(schema);
            case INTEGER, INT_ENUM -> decoder.readInteger(schema);
            case LONG -> decoder.readLong(schema);
            case DOUBLE -> decoder.readDouble(schema);
            case LIST, MAP, STRUCTURE, UNION -> deserializeAggregate(decoder, schema);
            default -> deserializeRareUnionScalar(decoder, schema);
        };
    }

    private Object deserializeRareUnionScalar(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case FLOAT -> decoder.readFloat(schema);
            case BYTE -> decoder.readByte(schema);
            case SHORT -> decoder.readShort(schema);
            case TIMESTAMP -> decoder.readTimestamp(schema);
            case BIG_DECIMAL -> decoder.readBigDecimal(schema);
            case BIG_INTEGER -> decoder.readBigInteger(schema);
            case BLOB -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, decoder.readDataStream(schema));
                }
                yield decoder.readBlob(schema);
            }
            // DOCUMENT members keep their Document wrapper (SchemaDocument); they're not scalars.
            case DOCUMENT -> new ContentDocument.SchemaDocument(schema, decoder.readDocument());
            default -> throw new UnsupportedOperationException("Unsupported target type: " + schema.type());
        };
    }

    private Document deserializeRareScalar(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case FLOAT -> new ContentDocument(schema, decoder.readFloat(schema));
            case BYTE -> new ContentDocument(schema, decoder.readByte(schema));
            case SHORT -> new ContentDocument(schema, decoder.readShort(schema));
            case TIMESTAMP -> new ContentDocument(schema, decoder.readTimestamp(schema));
            case BIG_DECIMAL -> new ContentDocument(schema, decoder.readBigDecimal(schema));
            case BIG_INTEGER -> new ContentDocument(schema, decoder.readBigInteger(schema));
            case DOCUMENT -> new ContentDocument.SchemaDocument(schema, decoder.readDocument());
            case BLOB -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, decoder.readDataStream(schema));
                }
                yield new ContentDocument(schema, decoder.readBlob(schema));
            }
            default -> throw new UnsupportedOperationException("Unsupported target type: " + schema.type());
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
        Schema savedSchema = listConsumer.schema;
        boolean savedSparse = listConsumer.sparse;
        listConsumer.schema = schema.listMember();
        listConsumer.sparse = schema.hasTrait(TraitKey.SPARSE_TRAIT);
        int size = decoder.containerSize();
        var items = size >= 0 && size <= decoder.containerPreAllocationLimit()
                ? new ArrayList<Document>(size)
                : new ArrayList<Document>(DEFAULT_COLLECTION_CAPACITY);
        decoder.readList(schema, items, listConsumer);
        listConsumer.schema = savedSchema;
        listConsumer.sparse = savedSparse;
        return new ContentDocument(schema, items);
    }

    private Document deserializeMap(ShapeDeserializer decoder, Schema schema) {
        Schema savedSchema = mapConsumer.schema;
        boolean savedSparse = mapConsumer.sparse;
        mapConsumer.schema = schema.mapValueMember();
        mapConsumer.sparse = schema.hasTrait(TraitKey.SPARSE_TRAIT);
        int size = decoder.containerSize();
        // LinkedHashMap: O(N) iteration (no empty-bucket scanning) and preserves wire order.
        var map = size >= 0 && size <= decoder.containerPreAllocationLimit()
                ? LinkedHashMap.<String, Document>newLinkedHashMap(size)
                : new LinkedHashMap<String, Document>(DEFAULT_COLLECTION_CAPACITY);
        decoder.readStringMap(schema, map, mapConsumer);
        mapConsumer.schema = savedSchema;
        mapConsumer.sparse = savedSparse;
        return new ContentDocument(schema, map);
    }

    private StructDocument createStructDocument(ShapeDeserializer decoder, Schema schema) {
        if (schema.type() == ShapeType.UNION) {
            return createUnionDocument(decoder, schema);
        }
        Document[] nestedValues = new Document[schema.members().size()];
        decoder.readStruct(schema, nestedValues, structConsumer);
        return new StructDocument(schema, nestedValues);
    }

    private StructDocument createUnionDocument(ShapeDeserializer decoder, Schema schema) {
        unionConsumer.value = null;
        unionConsumer.memberSchema = null;
        decoder.readStruct(schema, null, unionConsumer);
        return new StructDocument(schema, unionConsumer.value, unionConsumer.memberSchema);
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
        // Object, not Document: scalar members are stored raw (no ContentDocument wrapper); aggregates as Document.
        Object value;
        Schema memberSchema;

        @Override
        public void accept(Object state, Schema memberSchema, ShapeDeserializer memberDeserializer) {
            value = deserializeUnionValue(memberDeserializer, memberSchema);
            this.memberSchema = memberSchema;
        }
    }

    private final class ListConsumer implements ShapeDeserializer.ListMemberConsumer<List<Document>> {
        Schema schema;
        boolean sparse;

        @Override
        public void accept(List<Document> state, ShapeDeserializer memberDeserializer) {
            if (memberDeserializer.isNull()) {
                if (sparse) {
                    state.add(memberDeserializer.readNull());
                } else {
                    throw new SerializationException("Null value found in dense list: " + schema.id());
                }
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
            if (memberDeserializer.isNull()) {
                if (sparse) {
                    state.put(key, memberDeserializer.readNull());
                } else {
                    throw new SerializationException("Null value found in dense map: " + schema.id());
                }
            } else {
                state.put(key, deserializeValue(memberDeserializer, schema));
            }
        }
    }
}
