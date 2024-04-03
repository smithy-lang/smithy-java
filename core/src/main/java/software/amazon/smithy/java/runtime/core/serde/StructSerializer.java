/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Drives structure serialization.
 *
 * <p>Each serialization method requires an SdkSchema that is a member and should handle writing member names and
 * values on each write.
 */
public interface StructSerializer {

    /**
     * Explicitly ends the struct.
     *
     * <p>Calling this method should only be called when struct serialization is externally driven by
     * {@link ShapeSerializer#beginStruct(SdkSchema)}. Calling this method in other contexts will cause undefined
     * behavior.
     */
    void endStruct();

    /**
     * The primary method that handles writing members to a shape serializer.
     *
     * <p>Implementations should use {@link RequiredWriteSerializer} to ensure the consumer writes something when
     * called. If this assertion is not made, the serialization process can enter an undefined state.
     *
     * @param member       Member schema to write.
     * @param memberWriter Consumer that writes. The consumer is required to write something.
     */
    void member(SdkSchema member, Consumer<ShapeSerializer> memberWriter);

    default void booleanMember(SdkSchema member, boolean value) {
        member(member, writer -> writer.writeBoolean(member, value));
    }

    default void booleanMemberIf(SdkSchema member, Boolean value) {
        if (value != null) {
            booleanMember(member, value);
        }
    }

    default void byteMember(SdkSchema member, byte value) {
        member(member, writer -> writer.writeByte(member, value));
    }

    default void byteMemberIf(SdkSchema member, Byte value) {
        if (value != null) {
            byteMember(member, value);
        }
    }

    default void shortMember(SdkSchema member, short value) {
        member(member, writer -> writer.writeShort(member, value));
    }

    default void shortMemberIf(SdkSchema member, Short value) {
        if (value != null) {
            shortMember(member, value);
        }
    }

    default void integerMember(SdkSchema member, int value) {
        member(member, writer -> writer.writeInteger(member, value));
    }

    default void integerMemberIf(SdkSchema member, Integer value) {
        if (value != null) {
            integerMember(member, value);
        }
    }

    default void longMember(SdkSchema member, long value) {
        member(member, writer -> writer.writeLong(member, value));
    }

    default void longMemberIf(SdkSchema member, Long value) {
        if (value != null) {
            longMember(member, value);
        }
    }

    default void floatMember(SdkSchema member, float value) {
        member(member, writer -> writer.writeFloat(member, value));
    }

    default void floatMemberIf(SdkSchema member, Float value) {
        if (value != null) {
            floatMember(member, value);
        }
    }

    default void doubleMember(SdkSchema member, double value) {
        member(member, writer -> writer.writeDouble(member, value));
    }

    default void doubleMemberIf(SdkSchema member, Double value) {
        if (value != null) {
            doubleMember(member, value);
        }
    }

    default void bigIntegerMember(SdkSchema member, BigInteger value) {
        member(member, writer -> writer.writeBigInteger(member, value));
    }

    default void bigIntegerMemberIf(SdkSchema member, BigInteger value) {
        if (value != null) {
            bigIntegerMember(member, value);
        }
    }

    default void bigDecimalMember(SdkSchema member, BigDecimal value) {
        member(member, writer -> writer.writeBigDecimal(member, value));
    }

    default void bigDecimalMemberIf(SdkSchema member, BigDecimal value) {
        if (value != null) {
            bigDecimalMember(member, value);
        }
    }

    default void blobMember(SdkSchema member, byte[] value) {
        member(member, writer -> writer.writeBlob(member, value));
    }

    default void blobMemberIf(SdkSchema member, byte[] value) {
        if (value != null) {
            blobMember(member, value);
        }
    }

    default void stringMember(SdkSchema member, String value) {
        member(member, writer -> writer.writeString(member, value));
    }

    default void stringMemberIf(SdkSchema member, String value) {
        if (value != null) {
            stringMember(member, value);
        }
    }

    default void timestampMember(SdkSchema member, Instant value) {
        member(member, writer -> writer.writeTimestamp(member, value));
    }

    default void timestampMemberIf(SdkSchema member, Instant value) {
        if (value != null) {
            timestampMember(member, value);
        }
    }

    default void shapeMember(SdkSchema member, SerializableShape value) {
        member(member, value::serialize);
    }

    default void shapeMemberIf(SdkSchema member, SerializableShape value) {
        if (value != null) {
            shapeMember(member, value);
        }
    }

    default void documentMember(SdkSchema member, Document value) {
        member(member, writer -> writer.writeDocument(value));
    }

    default void documentMemberIf(SdkSchema member, Document value) {
        if (value != null) {
            documentMember(member, value);
        }
    }

    default void listMember(SdkSchema member, Consumer<ShapeSerializer> consumer) {
        member(member, writer -> writer.beginList(member, consumer));
    }

    default void mapMember(SdkSchema member, Consumer<MapSerializer> consumer) {
        member(member, writer -> writer.beginMap(member, consumer));
    }
}
