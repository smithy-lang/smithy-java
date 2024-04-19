/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.SerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.Pair;

final class TypedDocument implements Document {

    public static Document of(SerializableShape shape) {
        // Ensure the given serializer is for a document, get the schema, and get all members.
        var ser = new SpecificShapeSerializer() {
            private Schema documentSchema;
            private final Map<String, Pair<Schema, Consumer<ShapeSerializer>>> members = new LinkedHashMap<>();

            @Override
            protected RuntimeException throwForInvalidState(String message, Schema schema) {
                return new SerdeException("Typed documents can only wrap structures. " + message);
            }

            @Override
            public void writeStruct(Schema schema, Consumer<ShapeSerializer> consumer) {
                this.documentSchema = schema;
                consumer.accept(ShapeSerializer.ofDelegatingConsumer((member, memberWriter) -> {
                    members.put(member.memberName(), Pair.of(member, memberWriter));
                }));
            }
        };

        shape.serialize(ser);

        if (ser.documentSchema == null) {
            throw new SerdeException(
                "When attempting to create a typed document, the underlying shape "
                    + "serializer did not write any values. Expected it to write a structure."
            );
        }

        return new TypedDocument(shape, ser.documentSchema, ser.members);
    }

    private final Schema documentSchema;
    private final Map<String, Pair<Schema, Consumer<ShapeSerializer>>> members;
    private final SerializableShape shape;
    private volatile Document equalityValue;
    private volatile int computedHashCode;

    private TypedDocument(
        SerializableShape shape,
        Schema documentSchema,
        Map<String, Pair<Schema, Consumer<ShapeSerializer>>> members
    ) {
        this.shape = shape;
        this.documentSchema = documentSchema;
        this.members = members;
    }

    @Override
    public void serializeContents(ShapeSerializer encoder) {
        shape.serialize(encoder);
    }

    @Override
    public ShapeType type() {
        return documentSchema.type();
    }

    @Override
    public Document getMember(String memberName) {
        var entry = members.get(memberName);
        if (entry == null) {
            return null;
        }
        return new TypedDocumentMember(entry.left, entry.right);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("TypedDocument{schema=").append(documentSchema).append(", members=");
        int i = 0;
        for (var entry : members.entrySet()) {
            if (i++ > 0) {
                result.append(", ");
            }
            result.append("{").append(entry.getKey()).append("=").append(entry.getValue().left).append("}");
        }
        result.append("}");
        return result.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypedDocument that = (TypedDocument) o;

        // Ensure the document schema matches.
        if (!documentSchema.equals(that.documentSchema) || !members.keySet().equals(that.members.keySet())) {
            return false;
        }

        // Ensure the member schemas match.
        for (var entry : members.entrySet()) {
            var l = entry.getValue().left;
            var r = that.members.get(entry.getKey()).left;
            if (!l.equals(r)) {
                return false;
            }
        }

        // Compare equality based on the value of the serialized shapes.
        return getEqualityValue().equals(that.getEqualityValue());
    }

    // Equality of typed documents is based on the serialized value of the document.
    private Document getEqualityValue() {
        var eq = equalityValue;
        if (eq == null) {
            eq = equalityValue = Document.ofValue(this);
        }
        return eq;
    }

    @Override
    public int hashCode() {
        var hash = computedHashCode;
        if (hash == 0) {
            int memberHash = members.isEmpty() ? 0 : 1;
            for (var entry : members.entrySet()) {
                memberHash = 11 * memberHash + entry.getValue().left.hashCode();
            }
            hash = Objects.hash(documentSchema, memberHash, getEqualityValue());
            computedHashCode = hash;
        }

        return hash;
    }
}
