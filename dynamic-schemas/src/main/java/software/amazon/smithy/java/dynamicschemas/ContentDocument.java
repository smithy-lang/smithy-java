/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A leaf or collection document that carries a model {@link Schema} together with its already-unwrapped value.
 *
 * <p>This stands in for a non-structure, non-union, non-document member of a dynamic {@link StructDocument}: a scalar
 * (string, number, boolean, timestamp, blob) or an aggregate (list/set/map) whose elements are themselves documents.
 * Instead of wrapping a second {@link Document} purely to substitute the schema (which doubled the per-leaf allocation
 * count), it stores the raw Java value directly:
 *
 * <ul>
 *   <li>STRING / ENUM → {@link String}</li>
 *   <li>BOOLEAN → {@link Boolean}</li>
 *   <li>numeric types → a boxed {@link Number} (the wire type)</li>
 *   <li>TIMESTAMP → {@link Instant}</li>
 *   <li>BLOB → {@link ByteBuffer}</li>
 *   <li>LIST / SET → {@code List<Document>}</li>
 *   <li>MAP → {@code Map<String, Document>}</li>
 * </ul>
 *
 * <p>Untyped {@code document}-typed members are handled by {@link SchemaDocument} instead, since their value can be
 * any document kind and must be delegated to.
 *
 * <p>Keeping every leaf a single concrete type ({@code ContentDocument}) also keeps the {@code value.serialize(...)}
 * call site in {@link StructDocument#serializeMembers} bimorphic ({@code ContentDocument} + {@code StructDocument}),
 * which preserves inlining in the serialize path. Collection serialization passes {@code this} as the consumer state
 * and uses static method references so no capturing lambda is allocated per list/map.
 */
final class ContentDocument implements Document {

    private final Schema schema;
    private final Object value;

    ContentDocument(Schema schema, Object value) {
        this.schema = schema;
        this.value = value;
    }

    Schema schema() {
        return schema;
    }

    /**
     * The already-unwrapped Java value this document holds (a {@link String}, boxed {@link Number}, {@link Boolean},
     * {@link Instant}, {@link ByteBuffer}, {@code List<Document>}, or {@code Map<String, Document>}). Package-private so
     * {@link StructDocument} can collapse a scalar union member down to its raw value, skipping this wrapper.
     */
    Object rawValue() {
        return value;
    }

    /**
     * True when this document holds a scalar (not a list/set/map), i.e. its {@link #rawValue()} can be serialized
     * directly via {@link #serializeScalar} without a {@link Document} wrapper.
     */
    boolean isScalar() {
        return switch (schema.type()) {
            case LIST, SET, MAP -> false;
            default -> true;
        };
    }

    @Override
    public ShapeType type() {
        return schema.type();
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // A ContentDocument is never DOCUMENT-typed (untyped documents use SchemaDocument), so unlike the generic
        // Document contract it can serialize its contents directly without a writeDocument wrapper.
        serializeContents(serializer);
    }

    @Override
    public void serializeContents(ShapeSerializer serializer) {
        // Hot path: the common scalar leaves. Kept small (under C2's FreqInlineSize) so it inlines into the
        // monomorphic value.serialize(...) call site in StructDocument#serializeMembers. The bytecode-heavier arms
        // (big numbers, lists, maps — which need helper calls or method-reference bootstraps) live in a cold method
        // so they don't bloat this one past the inline budget. Verified via -XX:+PrintInlining.
        var s = schema;
        switch (s.type()) {
            case STRING, ENUM -> serializer.writeString(s, (String) value);
            case BOOLEAN -> serializer.writeBoolean(s, (Boolean) value);
            case INTEGER, INT_ENUM -> serializer.writeInteger(s, ((Number) value).intValue());
            case LONG -> serializer.writeLong(s, ((Number) value).longValue());
            case DOUBLE -> serializer.writeDouble(s, ((Number) value).doubleValue());
            case FLOAT -> serializer.writeFloat(s, ((Number) value).floatValue());
            case BYTE -> serializer.writeByte(s, ((Number) value).byteValue());
            case SHORT -> serializer.writeShort(s, ((Number) value).shortValue());
            case TIMESTAMP -> serializer.writeTimestamp(s, (Instant) value);
            default -> serializeNonScalar(serializer, s);
        }
    }

    /**
     * Serializes a raw, already-unwrapped scalar value against {@code schema}, without a {@link ContentDocument}
     * wrapper. Used by {@link StructDocument} for union members whose set value is a scalar — those are stored raw
     * (skipping the per-union {@code ContentDocument} allocation) and written directly here.
     *
     * <p>Mirrors the scalar arms of {@link #serializeContents}; the {@code LIST}/{@code MAP}/{@code DOCUMENT} cases
     * are never reached because those union members are kept as {@link Document} values and serialized via
     * {@code Document#serialize} instead. The arm order matches {@code serializeContents} so the common string/number
     * leaves stay hot.
     */
    static void serializeScalar(ShapeSerializer serializer, Schema schema, Object value) {
        switch (schema.type()) {
            case STRING, ENUM -> serializer.writeString(schema, (String) value);
            case BOOLEAN -> serializer.writeBoolean(schema, (Boolean) value);
            case INTEGER, INT_ENUM -> serializer.writeInteger(schema, ((Number) value).intValue());
            case LONG -> serializer.writeLong(schema, ((Number) value).longValue());
            case DOUBLE -> serializer.writeDouble(schema, ((Number) value).doubleValue());
            case FLOAT -> serializer.writeFloat(schema, ((Number) value).floatValue());
            case BYTE -> serializer.writeByte(schema, ((Number) value).byteValue());
            case SHORT -> serializer.writeShort(schema, ((Number) value).shortValue());
            case TIMESTAMP -> serializer.writeTimestamp(schema, (Instant) value);
            case BLOB -> serializer.writeBlob(schema, (ByteBuffer) value);
            case BIG_INTEGER -> serializer.writeBigInteger(schema, toBigInteger(value));
            case BIG_DECIMAL -> serializer.writeBigDecimal(schema, toBigDecimal(value));
            default -> throw new UnsupportedOperationException("Unsupported scalar type: " + schema.type());
        }
    }

    private static BigInteger toBigInteger(Object value) {
        return value instanceof BigInteger bi ? bi : BigInteger.valueOf(((Number) value).longValue());
    }

    private static BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(((Number) value).doubleValue());
    }

    private void serializeNonScalar(ShapeSerializer serializer, Schema s) {
        switch (s.type()) {
            case BLOB -> serializer.writeBlob(s, (ByteBuffer) value);
            case BIG_INTEGER -> serializer.writeBigInteger(s, asBigInteger());
            case BIG_DECIMAL -> serializer.writeBigDecimal(s, asBigDecimal());
            case LIST, SET -> serializer.writeList(s, this, size(), ContentDocument::serializeListContents);
            case MAP -> serializer.writeMap(s, this, size(), ContentDocument::serializeMapContents);
            default -> throw new UnsupportedOperationException("Unsupported type: " + s.type());
        }
    }

    // Non-capturing: receives the owning ContentDocument as state, so no per-call lambda is allocated.
    @SuppressWarnings("unchecked")
    private static void serializeListContents(ContentDocument doc, ShapeSerializer ser) {
        Schema member = doc.schema.listMember();
        List<Document> list = (List<Document>) doc.value;
        // The element type is the same for every element (it's the list member's schema), so hoist the type check out
        // of the loop. The common DynamoDB collections (SS, NS — both modeled as list<String>) are string lists; a
        // string element fast-path turns the per-element megamorphic chain (Document.serialize ->
        // ContentDocument.serializeContents -> switch) into a monomorphic, inlinable writeString loop, matching what
        // codegen emits for a typed StringSetSerializer. All other element types fall back to per-element dispatch via
        // serializeListElement (kept out of this method so the hot string loop stays small and inlinable).
        //
        // The list backing store is always an ArrayList (built by SchemaGuidedDocumentBuilder#deserializeList or
        // StructDocument#convertDocument), i.e. RandomAccess, so iterate by index: that avoids allocating an Iterator
        // and the per-element hasNext/modCount/bounds overhead of an enhanced-for, matching codegen's typed list
        // serializers which branch on RandomAccess and index. A non-RandomAccess fallback keeps the contract general.
        ShapeType memberType = member.type();
        if (memberType == ShapeType.STRING || memberType == ShapeType.ENUM) {
            if (list instanceof RandomAccess) {
                for (int i = 0, n = list.size(); i < n; i++) {
                    Document e = list.get(i);
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        ser.writeString(member, e.asString());
                    }
                }
            } else {
                for (Document e : list) {
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        ser.writeString(member, e.asString());
                    }
                }
            }
        } else {
            if (list instanceof RandomAccess) {
                for (int i = 0, n = list.size(); i < n; i++) {
                    Document e = list.get(i);
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        e.serialize(ser);
                    }
                }
            } else {
                for (Document e : list) {
                    if (e == null) {
                        ser.writeNull(member);
                    } else {
                        e.serialize(ser);
                    }
                }
            }
        }
    }

    // Cached singleton consumers passed to MapSerializer#writeEntry. All are non-capturing static method references,
    // so no per-entry lambda is allocated. The key reason these exist as named, concretely-typed consumers (rather
    // than a single Document::serialize ref) is to keep the value-write call site monomorphic: Document::serialize is
    // an invokeinterface across every Document implementation in the program (megamorphic — never inlines, and showed
    // up as standalone StructDocument.serialize / serializeContents frames on the serialize profile). Dispatching once
    // on the value member type (below) and handing writeEntry a consumer that operates on a concrete type lets the JIT
    // inline the write, matching what codegen emits for a typed map value serializer.
    private static final BiConsumer<Schema, ShapeSerializer> NULL_VALUE_WRITER = (vm, ser) -> ser.writeNull(vm);

    // Value is a structure/union: it's always a StructDocument. Call writeStruct directly with the struct's own
    // schema — identical output to codegen's `serializer.writeStruct(mapValueMember(), value)` (writeStruct resolves a
    // member schema to its target, which is this same struct/union schema) — skipping the Document.serialize ->
    // serializeContents -> writeStruct indirection entirely.
    private static final BiConsumer<StructDocument, ShapeSerializer> STRUCT_VALUE_WRITER =
            (d, ser) -> ser.writeStruct(d.schema(), d);

    // Value is a string/enum: it's always a ContentDocument whose schema is the map value member. Write the raw string
    // directly.
    private static final BiConsumer<ContentDocument, ShapeSerializer> STRING_VALUE_WRITER =
            (d, ser) -> ser.writeString(d.schema, (String) d.value);

    @SuppressWarnings("unchecked")
    private static void serializeMapContents(ContentDocument doc, MapSerializer ms) {
        Schema key = doc.schema.mapKeyMember();
        Schema valueMember = doc.schema.mapValueMember();
        Map<String, Document> map = (Map<String, Document>) doc.value;
        // The value type is the same for every entry (the map value member's schema), so hoist the dispatch out of the
        // loop and hand writeEntry a monomorphic, concretely-typed consumer per branch (see the consumer fields above).
        switch (valueMember.type()) {
            case STRUCTURE, UNION -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), (StructDocument) v, STRUCT_VALUE_WRITER);
                    }
                }
            }
            case STRING, ENUM -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), (ContentDocument) v, STRING_VALUE_WRITER);
                    }
                }
            }
            default -> {
                for (var entry : map.entrySet()) {
                    var v = entry.getValue();
                    if (v == null) {
                        ms.writeEntry(key, entry.getKey(), valueMember, NULL_VALUE_WRITER);
                    } else {
                        ms.writeEntry(key, entry.getKey(), v, Document::serialize);
                    }
                }
            }
        }
    }

    @Override
    public BigDecimal asBigDecimal() {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue());
    }

    @Override
    public BigInteger asBigInteger() {
        if (value instanceof BigInteger bi) {
            return bi;
        }
        return BigInteger.valueOf(((Number) value).longValue());
    }

    @Override
    public ByteBuffer asBlob() {
        return (ByteBuffer) value;
    }

    @Override
    public boolean asBoolean() {
        return (Boolean) value;
    }

    @Override
    public byte asByte() {
        return ((Number) value).byteValue();
    }

    @Override
    public double asDouble() {
        return ((Number) value).doubleValue();
    }

    @Override
    public float asFloat() {
        return ((Number) value).floatValue();
    }

    @Override
    public int asInteger() {
        return ((Number) value).intValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Document> asList() {
        return (List<Document>) value;
    }

    @Override
    public long asLong() {
        return ((Number) value).longValue();
    }

    @Override
    public Number asNumber() {
        return (Number) value;
    }

    @Override
    public Object asObject() {
        return switch (schema.type()) {
            case LIST, SET, MAP -> Document.super.asObject();
            // String, Boolean, boxed Number, Instant, ByteBuffer are all already valid asObject() results.
            default -> value;
        };
    }

    @Override
    public short asShort() {
        return ((Number) value).shortValue();
    }

    @Override
    public String asString() {
        return (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Document> asStringMap() {
        return (Map<String, Document>) value;
    }

    @Override
    public Instant asTimestamp() {
        return (Instant) value;
    }

    @Override
    public int size() {
        return switch (schema.type()) {
            case LIST, SET -> ((List<?>) value).size();
            case MAP -> ((Map<?, ?>) value).size();
            default -> -1;
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Document getMember(String memberName) {
        if (schema.type() == ShapeType.MAP) {
            return ((Map<String, Document>) value).get(memberName);
        }
        return Document.super.getMember(memberName);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getMemberNames() {
        if (schema.type() == ShapeType.MAP) {
            return ((Map<String, Document>) value).keySet();
        }
        return Document.super.getMemberNames();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContentDocument that)) {
            return false;
        }
        return schema.equals(that.schema) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * schema.hashCode() + Objects.hashCode(value);
    }
}
