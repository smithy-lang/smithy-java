/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A document implementation that also implements {@link SerializableStruct} so it can be used as a structure or union.
 *
 * <p>Note that this implementation does break the invariant of Document that {@link #serialize} always serializes
 * itself as a document, and then serializes the contents. That's because this implementation of Document is meant to
 * stand-in for a modeled value and not get serialized as a document.
 */
public final class StructDocument implements Document, SerializableStruct {

    private final Schema schema;
    private final ShapeId service;
    private final Document[] values;
    private final int setMemberIndex;
    private Map<String, Document> mapView;

    StructDocument(Schema schema, Document[] values, ShapeId service) {
        this.service = service;
        this.schema = schema;
        this.values = values;
        this.setMemberIndex = schema.type() == ShapeType.UNION ? findSetMember(values) : -1;
    }

    StructDocument(Schema schema, Document unionValue, int unionMemberIndex, ShapeId service) {
        this.service = service;
        this.schema = schema;
        this.values = new Document[]{unionValue};
        this.setMemberIndex = unionMemberIndex;
    }

    private static int findSetMember(Document[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Converts an untyped document to a typed document that can be used in place of a structure or union.
     *
     * <p>Uses the shape ID of the given schema to help resolve relative shape IDs in nested document discriminators.
     *
     * @param schema Schema to assign to the converted document. Must be a structure or union schema.
     * @param delegate The document to convert.
     * @return the converted document.
     * @throws IllegalArgumentException if the schema isn't for a structure or the document isn't a map or structure.
     */
    public static StructDocument of(Schema schema, Document delegate) {
        return of(schema, delegate, schema.id());
    }

    /**
     * Converts an untyped document to a typed document that can be used in place of a structure or union.
     *
     * @param schema Schema to assign to the converted document. Must be a structure or union schema.
     * @param delegate The document to convert.
     * @param service The shape ID of a service, used to provide a default namespace to relative document shape IDs.
     * @return the converted document.
     * @throws IllegalArgumentException if the schema isn't for a structure or the document isn't a map or structure.
     */
    public static StructDocument of(Schema schema, Document delegate, ShapeId service) {
        var schemaType = schema.type();
        if (schemaType != ShapeType.STRUCTURE && schemaType != ShapeType.UNION) {
            throw new IllegalArgumentException("Schema must be a structure or union, got " + schemaType);
        }

        var delegateType = delegate.type();
        if (delegateType == ShapeType.MAP || delegateType == ShapeType.STRUCTURE || delegateType == ShapeType.UNION) {
            return (StructDocument) convertDocument(schema, delegate, service);
        }

        throw new IllegalArgumentException("Document must be a map, structure, or union, but got " + delegate.type());
    }

    private static Document convertStructureDocument(Schema schema, Document delegate, ShapeId service) {
        List<Schema> schemaMembers = schema.members();
        if (schema.type() == ShapeType.UNION) {
            for (int i = 0, n = schemaMembers.size(); i < n; i++) {
                Schema member = schemaMembers.get(i);
                var value = delegate.getMember(member.memberName());
                if (value != null) {
                    return new StructDocument(schema, convertDocument(member, value, service),
                            member.memberIndex(), service);
                }
            }
            return new StructDocument(schema, new Document[0], service);
        }
        Document[] result = new Document[schemaMembers.size()];
        for (int i = 0, n = schemaMembers.size(); i < n; i++) {
            Schema member = schemaMembers.get(i);
            var value = delegate.getMember(member.memberName());
            if (value != null) {
                result[member.memberIndex()] = convertDocument(member, value, service);
            }
        }
        return new StructDocument(schema, result, service);
    }

    static Document convertDocument(Schema schema, Document delegate, ShapeId service) {
        return switch (schema.type()) {
            case STRUCTURE -> convertStructureDocument(schema, delegate, service);
            case UNION -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, delegate.asEventStream());
                }
                yield convertStructureDocument(schema, delegate, service);
            }
            case MAP -> {
                Map<String, Document> result = new LinkedHashMap<>();
                var valueMember = schema.mapValueMember();
                for (var entry : delegate.asStringMap().entrySet()) {
                    if (entry.getValue() == null) {
                        result.put(entry.getKey(), null);
                    } else {
                        result.put(entry.getKey(), convertDocument(valueMember, entry.getValue(), service));
                    }
                }
                yield new ContentDocument(Document.of(result), schema);
            }
            case LIST, SET -> {
                List<Document> result = new ArrayList<>();
                var valueMember = schema.listMember();
                for (var value : delegate.asList()) {
                    if (value == null) {
                        result.add(null);
                    } else {
                        result.add(convertDocument(valueMember, value, service));
                    }
                }
                yield new ContentDocument(Document.of(result), schema);
            }
            case BOOLEAN -> new ContentDocument(Document.of(delegate.asBoolean()), schema);
            case STRING, ENUM -> new ContentDocument(Document.of(delegate.asString()), schema);
            case TIMESTAMP -> new ContentDocument(Document.of(delegate.asTimestamp()), schema);
            case BYTE, SHORT, INTEGER, INT_ENUM,
                    LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL ->
                new ContentDocument(Document.ofNumber(delegate.asNumber()), schema);
            case DOCUMENT -> new ContentDocument(delegate, schema);
            case BLOB -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, delegate.asDataStream());
                }
                yield new ContentDocument(Document.of(delegate.asBlob()), schema);
            }
            default -> throw new IllegalArgumentException("Unsupported schema type: " + schema);
        };
    }

    @Override
    public Schema schema() {
        return schema;
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializeContents(serializer);
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        serializer.writeStruct(schema, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        int idx = setMemberIndex;
        if (idx >= 0) {
            values[0].serialize(serializer);
        } else {
            for (int i = 0; i < values.length; i++) {
                Document value = values[i];
                if (value != null) {
                    value.serialize(serializer);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberInSchema(schema, member, null);
        Document value;
        if (setMemberIndex >= 0) {
            value = member.memberIndex() == setMemberIndex ? values[0] : null;
        } else {
            int idx = member.memberIndex();
            value = idx < values.length ? values[idx] : null;
        }
        if (value == null) {
            return null;
        }
        try {
            return (T) value.asObject();
        } catch (ClassCastException e) {
            throw new ClassCastException(
                    "Unable to cast document member `" + member.id() + "` from document with schema `" + schema
                            .id() + "`: " + e.getMessage());
        }
    }

    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public ShapeId discriminator() {
        return schema.type() == ShapeType.STRUCTURE ? schema.id() : null;
    }

    @Override
    public int size() {
        if (setMemberIndex >= 0) {
            return 1;
        }
        int count = 0;
        for (Document v : values) {
            if (v != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Map<String, Document> asStringMap() {
        Map<String, Document> result = mapView;
        if (result == null) {
            if (setMemberIndex >= 0) {
                result = Map.of(schema.members().get(setMemberIndex).memberName(), values[0]);
            } else {
                result = new LinkedHashMap<>();
                List<Schema> schemaMembers = schema.members();
                for (int i = 0, n = schemaMembers.size(); i < n; i++) {
                    Document value = i < values.length ? values[i] : null;
                    if (value != null) {
                        result.put(schemaMembers.get(i).memberName(), value);
                    }
                }
                result = Collections.unmodifiableMap(result);
            }
            mapView = result;
        }
        return result;
    }

    @Override
    public Object asObject() {
        if (setMemberIndex >= 0) {
            return Map.of(schema.members().get(setMemberIndex).memberName(), values[0].asObject());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        List<Schema> schemaMembers = schema.members();
        for (int i = 0, n = schemaMembers.size(); i < n; i++) {
            Document value = i < values.length ? values[i] : null;
            if (value != null) {
                result.put(schemaMembers.get(i).memberName(), value.asObject());
            }
        }
        return result;
    }

    @Override
    public Document getMember(String memberName) {
        Schema memberSchema = schema.member(memberName);
        if (memberSchema == null) {
            return null;
        }
        int idx = memberSchema.memberIndex();
        if (setMemberIndex >= 0) {
            return idx == setMemberIndex ? values[0] : null;
        }
        return idx < values.length ? values[idx] : null;
    }

    @Override
    public Set<String> getMemberNames() {
        return asStringMap().keySet();
    }
}
