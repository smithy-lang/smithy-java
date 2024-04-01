/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

/**
 * This is essentially responsible for serializing an Any to JSON, XML, etc.
 */
final class AnySerializer {

    private final Any value;

    AnySerializer(Any value) {
        this.value = value;
    }

    void serialize(ShapeSerializer encoder) {
        SdkSchema schema = value.getSchema();
        switch (value.getType()) {
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
            case DOCUMENT -> encoder.writeDocument(schema, value);
            case MAP -> encoder.beginMap(schema, mapSerializer -> {
                for (var entry : value.asMap().entrySet()) {
                    switch (entry.getKey().getType()) {
                        case INTEGER, INT_ENUM ->
                            mapSerializer.entry(entry.getKey().asInteger(), c -> entry.getValue().serialize(c));
                        case LONG -> mapSerializer.entry(entry.getKey().asLong(), c -> entry.getValue().serialize(c));
                        case STRING, ENUM ->
                            mapSerializer.entry(entry.getKey().asString(), c -> entry.getValue().serialize(c));
                        default -> throw new UnsupportedOperationException(
                                "Unsupported document type map key: " + entry.getKey().getType()
                        );
                    }
                }
            });
            case LIST -> encoder.beginList(schema, c -> {
                for (Any value : value.asList()) {
                    value.serialize(c);
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
            default -> throw new UnsupportedOperationException("Cannot serialize document of type " + value.getType());
        }
    }
}
