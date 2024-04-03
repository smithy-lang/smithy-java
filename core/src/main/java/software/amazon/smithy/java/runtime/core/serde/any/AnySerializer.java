/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * A generic implementation to serialize an Any type.
 *
 * <p>The built-in Any types override {@link Any#serialize(ShapeSerializer)}, but some implementations might choose
 * to delegate to the default implementation (e.g., see JsonAny from json-codec).
 */
final class AnySerializer {

    private AnySerializer() {
    }

    static void serialize(Any value, ShapeSerializer encoder) {
        SdkSchema schema = value.schema();
        switch (value.type()) {
            case BOOLEAN -> encoder.writeBoolean(schema, value.asBoolean());
            case BYTE -> encoder.writeByte(schema, value.asByte());
            case SHORT -> encoder.writeShort(schema, value.asShort());
            case INTEGER, INT_ENUM -> encoder.writeInteger(schema, value.asInteger());
            case LONG -> encoder.writeLong(schema, value.asLong());
            case FLOAT -> encoder.writeFloat(schema, value.asFloat());
            case DOUBLE -> encoder.writeDouble(schema, value.asDouble());
            case BIG_INTEGER -> encoder.writeBigInteger(schema, value.asBigInteger());
            case BIG_DECIMAL -> encoder.writeBigDecimal(schema, value.asBigDecimal());
            case STRING, ENUM -> encoder.writeString(schema, value.asString());
            case BLOB -> encoder.writeBlob(schema, value.asBlob());
            case TIMESTAMP -> encoder.writeTimestamp(schema, value.asTimestamp());
            case DOCUMENT -> encoder.writeDocument(value);
            case MAP -> encoder.beginMap(schema, mapSerializer -> {
                for (var entry : value.asMap().entrySet()) {
                    switch (entry.getKey().type()) {
                        case INTEGER, INT_ENUM ->
                            mapSerializer.entry(entry.getKey().asInteger(), c -> entry.getValue().serialize(c));
                        case LONG -> mapSerializer.entry(entry.getKey().asLong(), c -> entry.getValue().serialize(c));
                        case STRING, ENUM ->
                            mapSerializer.entry(entry.getKey().asString(), c -> entry.getValue().serialize(c));
                        default -> throw new UnsupportedOperationException(
                            "Unsupported document type map key: " + entry.getKey().type()
                        );
                    }
                }
            });
            case LIST -> encoder.beginList(schema, c -> {
                for (Any entry : value.asList()) {
                    entry.serialize(c);
                }
            });
            case STRUCTURE, UNION -> encoder.beginStruct(schema, structSerializer -> {
                for (SdkSchema member : schema.members()) {
                    if (member != null) {
                        Any memberValue = value.getStructMember(member.memberName());
                        if (memberValue != null) {
                            structSerializer.member(member, memberValue::serialize);
                        }
                    }
                }
            });
            default -> encoder.writeShape(value);
        }
    }
}
