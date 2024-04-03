/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.StructSerializer;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.Pair;

final class TypedDocument implements Document {

    public static Document of(SerializableShape shape) {
        return of((Consumer<ShapeSerializer>) shape::serialize);
    }

    public static Document of(Consumer<ShapeSerializer> writer) {
        // Ensure the given serializer is for a document, get the schema, and get all members.
        var ser = new SpecificShapeSerializer() {
            private SdkSchema documentSchema;
            private final Map<String, Pair<SdkSchema, Consumer<ShapeSerializer>>> members = new LinkedHashMap<>();

            @Override
            protected RuntimeException throwForInvalidState(SdkSchema schema) {
                return new SdkSerdeException("Typed documents can only wrap structures. Found: " + schema);
            }

            @Override
            public StructSerializer beginStruct(SdkSchema schema) {
                this.documentSchema = schema;
                return new StructSerializer() {
                    @Override
                    public void endStruct() {}

                    @Override
                    public void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter) {
                        members.put(member.memberName(), Pair.of(member, memberWriter));
                    }
                };
            }
        };

        writer.accept(ser);

        if (ser.documentSchema == null) {
            throw new SdkSerdeException(
                "When attempting to create a typed document, the underlying shape "
                    + "serializer did not write any values. Expected it to write a structure."
            );
        }

        return new TypedDocument(writer, ser.documentSchema, ser.members);
    }

    private final SdkSchema documentSchema;
    private final Map<String, Pair<SdkSchema, Consumer<ShapeSerializer>>> members;
    private final Consumer<ShapeSerializer> serializer;
    private volatile Document equalityValue;
    private volatile int computedHashCode;

    private TypedDocument(
        Consumer<ShapeSerializer> serializer,
        SdkSchema documentSchema,
        Map<String, Pair<SdkSchema, Consumer<ShapeSerializer>>> members
    ) {
        this.serializer = serializer;
        this.documentSchema = documentSchema;
        this.members = members;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        serializer.accept(encoder);
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
