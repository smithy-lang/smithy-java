/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

final class Documents {

    static final Schema LIST_SCHEMA = Schema.listBuilder(PreludeSchemas.DOCUMENT.id())
            .putMember("member", PreludeSchemas.DOCUMENT)
            .build();

    static final Schema STR_MAP_SCHEMA = Schema.mapBuilder(PreludeSchemas.DOCUMENT.id())
            .putMember("key", PreludeSchemas.STRING)
            .putMember("value", PreludeSchemas.DOCUMENT)
            .build();

    private Documents() {}

    record BooleanDocument(Schema schema, boolean value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BOOLEAN;
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBoolean(schema, value);
        }
    }

    record NumberDocument(Schema schema, Number value) implements Document {
        @Override
        public ShapeType type() {
            return schema.type();
        }

        @Override
        public byte asByte() {
            return value.byteValue();
        }

        @Override
        public short asShort() {
            return value.shortValue();
        }

        @Override
        public int asInteger() {
            return value.intValue();
        }

        @Override
        public long asLong() {
            return value.longValue();
        }

        @Override
        public float asFloat() {
            return value.floatValue();
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public BigInteger asBigInteger() {
            return value instanceof BigInteger ? (BigInteger) value : BigInteger.valueOf(value.longValue());
        }

        @Override
        public BigDecimal asBigDecimal() {
            return value instanceof BigDecimal ? (BigDecimal) value : BigDecimal.valueOf(value.doubleValue());
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            DocumentUtils.serializeNumber(serializer, schema, value);
        }
    }

    record StringDocument(Schema schema, String value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.STRING;
        }

        @Override
        public String asString() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeString(schema, value);
        }
    }

    record BlobDocument(Schema schema, ByteBuffer value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.BLOB;
        }

        @Override
        public ByteBuffer asBlob() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeBlob(schema, value);
        }

        // Records don't generate this same equals behavior for byte arrays, so customize it.
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            } else {
                BlobDocument that = (BlobDocument) o;
                return value.equals(that.value);
            }
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }

    record TimestampDocument(Schema schema, Instant value) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.TIMESTAMP;
        }

        @Override
        public Instant asTimestamp() {
            return value;
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeTimestamp(schema, value);
        }
    }

    record ListDocument(Schema schema, List<Document> values) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.LIST;
        }

        @Override
        public List<Document> asList() {
            return values;
        }

        @Override
        public int size() {
            return values.size();
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeList(schema, values, values.size(), (values, ser) -> {
                for (var element : values) {
                    if (element == null) {
                        ser.writeNull(schema.listMember());
                    } else {
                        element.serialize(ser);
                    }
                }
            });
        }
    }

    record StringMapDocument(Schema schema, Map<String, Document> members) implements Document {
        @Override
        public ShapeType type() {
            return ShapeType.MAP;
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }

        @Override
        public int size() {
            return members.size();
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return Set.copyOf(members.keySet());
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            serializer.writeMap(schema, members, members.size(), (members, s) -> {
                var key = schema.mapKeyMember();
                for (var entry : members.entrySet()) {
                    s.writeEntry(key, entry.getKey(), entry.getValue(), (doc, ser) -> {
                        if (doc == null) {
                            ser.writeNull(schema.mapValueMember());
                        } else {
                            doc.serialize(ser);
                        }
                    });
                }
            });
        }
    }

    record StructureDocument(Schema schema, Map<String, Document> members) implements Document, SerializableStruct {
        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public Document getMember(String memberName) {
            return members.get(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return Set.copyOf(members.keySet());
        }

        @Override
        public Map<String, Document> asStringMap() {
            return members;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            // De-conflict Document and SerializableStruct default implementations.
            Document.super.serialize(serializer);
        }

        // Note that this method is never actually called right now because LazyStructure doesn't delegate to it.
        @Override
        public void serializeContents(ShapeSerializer encoder) {
            encoder.writeStruct(schema, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            for (var entry : members.entrySet()) {
                entry.getValue().serialize(serializer);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return DocumentUtils.getMemberValue(this, schema, member);
        }
    }

    /**
     * A document that wraps a shape and a schema, lazily creating the document only if needed.
     */
    static final class LazyStructure implements Document, SerializableStruct {

        private final Schema schema;
        private final SerializableStruct struct;
        private volatile transient StructureDocument createdDocument;

        LazyStructure(Schema schema, SerializableStruct struct) {
            this.schema = schema;
            this.struct = struct;
        }

        private StructureDocument getDocument() {
            var result = createdDocument;
            if (result == null) {
                var parser = new DocumentParser.StructureParser();
                struct.serializeMembers(parser);
                result = new StructureDocument(schema, parser.members());
                createdDocument = result;
            }
            return result;
        }

        @Override
        public ShapeType type() {
            return ShapeType.STRUCTURE;
        }

        @Override
        public ShapeId discriminator() {
            return schema.id();
        }

        @Override
        public Map<String, Document> asStringMap() {
            return getDocument().asStringMap();
        }

        @Override
        public Document getMember(String memberName) {
            return getDocument().getMember(memberName);
        }

        @Override
        public Set<String> getMemberNames() {
            return getDocument().getMemberNames();
        }

        @Override
        public String toString() {
            return getDocument().toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj instanceof LazyStructure) {
                return getDocument().equals(((LazyStructure) obj).getDocument());
            } else if (obj instanceof Document) {
                return getDocument().equals(obj);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return getDocument().hashCode();
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            // De-conflict Document and SerializableStruct default implementations.
            Document.super.serialize(serializer);
        }

        @Override
        public void serializeContents(ShapeSerializer serializer) {
            struct.serialize(serializer);
        }

        @Override
        public Schema schema() {
            return schema;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            getDocument().serialize(serializer);
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return getDocument().getMemberValue(member);
        }
    }
}
