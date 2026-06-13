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
    // For a structure: a member-indexed array of values (never null). For a union: always null — a union has at most
    // one set member, held directly in {@link #unionValue}, so it never pays for a (mostly-empty) array. The
    // {@code values == null} test is the (branch-cheap) union/structure discriminator on the hot paths.
    private final Document[] values;
    // For a union: the single set member's value (null when no member is set). Unused for structures.
    //
    // <p>This is deliberately typed {@code Object}, not {@code Document}: when the set member is a scalar (string,
    // number, boolean, timestamp, blob, big number — anything {@link ContentDocument#isScalar()}), we store the raw,
    // already-unwrapped value here and skip the per-union {@code ContentDocument} wrapper entirely — the single
    // biggest allocation win for union-dense payloads like DynamoDB's {@code AttributeValue} (every attribute is a
    // union). Aggregate/struct/document members are kept as their {@link Document} and stored here as-is. The two
    // cases are told apart with a cheap {@code instanceof Document} test on the cold read paths; the hot serialize
    // path branches on it once. {@link #unionValueAsDocument()} re-wraps a raw scalar on demand for the
    // Document-returning accessors.
    private final Object unionValue;
    // For a union: the schema of the set member, or null when none is set. Always null for structures. Caching the
    // member schema directly (rather than just its index) means the hot serialize path and the cold read paths never
    // have to re-resolve it via schema.members().get(index) — a list lookup per union that otherwise showed up on
    // the serialize profile.
    private final Schema unionMemberSchema;
    private Map<String, Document> mapView;

    StructDocument(Schema schema, Document[] values) {
        this.schema = schema;
        if (schema.type() == ShapeType.UNION) {
            // Collapse the per-member array down to the single set value; unions never retain the array.
            int idx = findSetMember(values);
            this.values = null;
            this.unionValue = idx >= 0 ? unwrapScalar(values[idx]) : null;
            this.unionMemberSchema = idx >= 0 ? schema.members().get(idx) : null;
        } else {
            this.values = values;
            this.unionValue = null;
            this.unionMemberSchema = null;
        }
    }

    StructDocument(Schema schema, Object unionValue, Schema unionMemberSchema) {
        this.schema = schema;
        this.values = null;
        this.unionValue = unionValue;
        this.unionMemberSchema = unionMemberSchema;
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
     * If {@code d} is a scalar {@link ContentDocument}, return its raw unwrapped value so a union can hold it without
     * the wrapper; otherwise return the document unchanged (aggregates/structs/unions/documents stay {@link Document}).
     */
    private static Object unwrapScalar(Document d) {
        if (d instanceof ContentDocument cd && cd.isScalar()) {
            return cd.rawValue();
        }
        return d;
    }

    /**
     * The set union member's value as a {@link Document}, re-wrapping a raw scalar in a {@link ContentDocument} on
     * demand. Cold path — used only by the {@code Document}-returning map/member accessors, never during serde.
     */
    private Document unionValueAsDocument() {
        Object uv = unionValue;
        if (uv == null) {
            return null;
        }
        if (uv instanceof Document d) {
            return d;
        }
        return new ContentDocument(unionMemberSchema, uv);
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
                    return new StructDocument(schema,
                            unwrapScalar(convertDocument(member, value, service)),
                            member);
                }
            }
            return new StructDocument(schema, (Object) null, (Schema) null);
        }
        Document[] result = new Document[schemaMembers.size()];
        for (int i = 0, n = schemaMembers.size(); i < n; i++) {
            Schema member = schemaMembers.get(i);
            var value = delegate.getMember(member.memberName());
            if (value != null) {
                result[member.memberIndex()] = convertDocument(member, value, service);
            }
        }
        return new StructDocument(schema, result);
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
                yield new ContentDocument(schema, result);
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
                yield new ContentDocument(schema, result);
            }
            case BOOLEAN -> new ContentDocument(schema, delegate.asBoolean());
            case STRING, ENUM -> new ContentDocument(schema, delegate.asString());
            case TIMESTAMP -> new ContentDocument(schema, delegate.asTimestamp());
            case BYTE, SHORT, INTEGER, INT_ENUM,
                    LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL ->
                new ContentDocument(schema, delegate.asNumber());
            case DOCUMENT -> new SchemaDocument(schema, delegate);
            case BLOB -> {
                if (schema.hasTrait(TraitKey.STREAMING_TRAIT)) {
                    yield Document.of(schema, delegate.asDataStream());
                }
                yield new ContentDocument(schema, delegate.asBlob());
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
        Document[] values = this.values;
        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                Document value = values[i];
                if (value != null) {
                    value.serialize(serializer);
                }
            }
        } else {
            Object uv = unionValue;
            if (uv instanceof Document d) {
                // Aggregate / struct / document union member: serialize through the Document.
                d.serialize(serializer);
            } else if (uv != null) {
                // Scalar union member stored raw (no ContentDocument wrapper): write it directly against the member
                // schema. Skips both the wrapper allocation and the extra virtual ContentDocument.serialize hop.
                ContentDocument.serializeScalar(serializer, unionMemberSchema, uv);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberInSchema(schema, member, null);
        Object value;
        if (values != null) {
            int idx = member.memberIndex();
            value = idx < values.length ? values[idx] : null;
        } else {
            value = unionMemberSchema != null && member.memberIndex() == unionMemberSchema.memberIndex()
                    ? unionValue
                    : null;
        }
        if (value == null) {
            return null;
        }
        try {
            // A raw scalar union value is already a valid asObject() result (String/Number/Boolean/Instant/...);
            // a Document (structure, aggregate, document member, or any structure member value) is unwrapped.
            return (T) (value instanceof Document d ? d.asObject() : value);
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
        Document[] values = this.values;
        if (values != null) {
            int count = 0;
            for (Document v : values) {
                if (v != null) {
                    count++;
                }
            }
            return count;
        }
        return unionValue != null ? 1 : 0;
    }

    @Override
    public Map<String, Document> asStringMap() {
        Map<String, Document> result = mapView;
        if (result == null) {
            Document[] values = this.values;
            if (values != null) {
                result = new LinkedHashMap<>();
                List<Schema> schemaMembers = schema.members();
                for (int i = 0, n = schemaMembers.size(); i < n; i++) {
                    Document value = i < values.length ? values[i] : null;
                    if (value != null) {
                        result.put(schemaMembers.get(i).memberName(), value);
                    }
                }
                result = Collections.unmodifiableMap(result);
            } else {
                result = unionValue != null
                        ? Map.of(unionMemberSchema.memberName(), unionValueAsDocument())
                        : Map.of();
            }
            mapView = result;
        }
        return result;
    }

    @Override
    public Object asObject() {
        Document[] values = this.values;
        if (values != null) {
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
        if (unionValue == null) {
            return Map.of();
        }
        Object uv = unionValue;
        Object asObject = uv instanceof Document d ? d.asObject() : uv;
        return Map.of(unionMemberSchema.memberName(), asObject);
    }

    @Override
    public Document getMember(String memberName) {
        Schema memberSchema = schema.member(memberName);
        if (memberSchema == null) {
            return null;
        }
        int idx = memberSchema.memberIndex();
        Document[] values = this.values;
        if (values != null) {
            return idx < values.length ? values[idx] : null;
        }
        return unionMemberSchema != null && idx == unionMemberSchema.memberIndex()
                ? unionValueAsDocument()
                : null;
    }

    @Override
    public Set<String> getMemberNames() {
        return asStringMap().keySet();
    }
}
