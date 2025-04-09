/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Allows a StructDocument to be used in a ShapeBuilder.
 */
final class SchemaGuidedDocumentBuilder implements ShapeBuilder<StructDocument> {

    private final ShapeId service;
    private final Schema target;
    private final Map<String, Document> map = new LinkedHashMap<>();

    SchemaGuidedDocumentBuilder(Schema target, ShapeId service) {
        if (target.type() != ShapeType.STRUCTURE && target.type() != ShapeType.UNION) {
            throw new IllegalArgumentException("StructDocument can only deserialize a structure or union, "
                    + "but got " + target);
        }

        this.target = target;
        this.service = service;
    }

    @Override
    public Schema schema() {
        return target;
    }

    @Override
    public StructDocument build() {
        if (map.isEmpty() && target.type() == ShapeType.UNION) {
            throw new IllegalArgumentException("No value set for union document: " + schema().id());
        } else {
            // Use "new" here since the document is already properly wrapped throughout.
            return new StructDocument(target, map, service);
        }
    }

    @Override
    public void setMemberValue(Schema member, Object value) {
        SchemaUtils.validateMemberInSchema(target, member, value);
        Document convertedValue;

        if (value instanceof Document d) {
            // Convert the given document so it matches the required schema.
            convertedValue = StructDocument.convertDocument(member, d, service);
        } else {
            // Convert the object to a document and then wrap it with the correct schema.
            convertedValue = Document.ofObject(value);
            convertedValue = StructDocument.convertDocument(member, convertedValue, service);
        }

        map.put(member.memberName(), convertedValue);
    }

    @Override
    public ShapeBuilder<StructDocument> deserialize(ShapeDeserializer decoder) {
        map.putAll(deserialize(decoder, target).asStringMap());
        return this;
    }

    @Override
    public ShapeBuilder<StructDocument> deserializeMember(ShapeDeserializer decoder, Schema schema) {
        map.putAll(deserialize(decoder, schema.assertMemberTargetIs(target)).asStringMap());
        return this;
    }

    private Document deserialize(ShapeDeserializer decoder, Schema schema) {
        return switch (schema.type()) {
            case BLOB -> new ContentDocument(Document.of(decoder.readBlob(schema)), schema);
            case BOOLEAN -> new ContentDocument(Document.of(decoder.readBoolean(schema)), schema);
            case STRING, ENUM -> new ContentDocument(Document.of(decoder.readString(schema)), schema);
            case TIMESTAMP -> new ContentDocument(Document.of(decoder.readTimestamp(schema)), schema);
            case BYTE -> new ContentDocument(Document.ofNumber(decoder.readByte(schema)), schema);
            case SHORT -> new ContentDocument(Document.ofNumber(decoder.readShort(schema)), schema);
            case INTEGER, INT_ENUM -> new ContentDocument(Document.ofNumber(decoder.readInteger(schema)), schema);
            case LONG -> new ContentDocument(Document.ofNumber(decoder.readLong(schema)), schema);
            case FLOAT -> new ContentDocument(Document.ofNumber(decoder.readFloat(schema)), schema);
            case DOCUMENT -> new ContentDocument(decoder.readDocument(), schema);
            case DOUBLE -> new ContentDocument(Document.ofNumber(decoder.readDouble(schema)), schema);
            case BIG_DECIMAL -> new ContentDocument(Document.ofNumber(decoder.readBigDecimal(schema)), schema);
            case BIG_INTEGER -> new ContentDocument(Document.ofNumber(decoder.readBigInteger(schema)), schema);
            case LIST -> {
                var items = new SchemaList(schema.listMember());
                decoder.readList(schema, items, (it, memberDeserializer) -> {
                    it.add(deserialize(memberDeserializer, it.schema));
                });
                yield new ContentDocument(Document.of(items), schema);
            }
            case MAP -> {
                var map = new SchemaMap(schema);
                decoder.readStringMap(schema, map, (state, mapKey, memberDeserializer) -> {
                    state.put(mapKey, deserialize(memberDeserializer, state.schema.mapValueMember()));
                });
                yield new ContentDocument(Document.of(map), schema);
            }
            case STRUCTURE, UNION -> {
                var map = new HashMap<String, Document>();
                decoder.readStruct(schema, map, (state, memberSchema, memberDeserializer) -> {
                    state.put(memberSchema.memberName(), deserialize(memberDeserializer, memberSchema));
                });
                // Use "new" here since we know all the nested shapes have the correct schemas.
                yield new StructDocument(schema, map, service);
            }
            default -> throw new UnsupportedOperationException("Unsupported target type: " + schema.type());
        };
    }

    @Override
    public ShapeBuilder<StructDocument> errorCorrection() {
        // TODO: fill in defaults.
        return this;
    }

    // Captures the schema of a list to pass to a closure.
    private static final class SchemaList extends ArrayList<Document> {
        private final Schema schema;

        SchemaList(Schema schema) {
            this.schema = schema;
        }
    }

    // Captures the schema of a map to pass to a closure.
    private static final class SchemaMap extends HashMap<String, Document> {
        private final Schema schema;

        SchemaMap(Schema schema) {
            this.schema = schema;
        }
    }
}
