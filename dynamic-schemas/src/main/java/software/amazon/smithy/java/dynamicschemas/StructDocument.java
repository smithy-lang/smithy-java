/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.core.serde.document.DocumentUtils;
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
    private final Map<String, Document> members;

    StructDocument(Schema schema, Map<String, Document> members, ShapeId service) {
        this.service = service;
        this.schema = schema;
        this.members = members;
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

    static Document convertDocument(Schema schema, Document delegate, ShapeId service) {
        return switch (schema.type()) {
            case STRUCTURE, UNION -> {
                Map<String, Document> result = new LinkedHashMap<>();
                for (var member : schema.members()) {
                    var value = delegate.getMember(member.memberName());
                    if (value != null) {
                        result.put(member.memberName(), convertDocument(member, value, service));
                    }
                }
                yield new StructDocument(schema, result, service);
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
            case BLOB -> new ContentDocument(Document.of(delegate.asBlob()), schema);
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
        for (var name : getMemberNames()) {
            var value = getMember(name);
            if (value != null) {
                var member = schema.member(name);
                if (member != null) {
                    value.serializeContents(serializer);
                }
            }
        }
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return DocumentUtils.getMemberValue(this, schema, member);
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
        return members.size();
    }

    @Override
    public Map<String, Document> asStringMap() {
        return members;
    }

    @Override
    public Object asObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : members.entrySet()) {
            result.put(entry.getKey(), entry.getValue().asObject());
        }
        return result;
    }

    @Override
    public Document getMember(String memberName) {
        return members.get(memberName);
    }

    @Override
    public Set<String> getMemberNames() {
        return members.keySet();
    }
}
