/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public interface ExampleUnion extends SerializableStruct {
    ShapeId ID = ShapeId.from("smithy.foo#ExampleUnion");

    SdkSchema SCHEMA_STRING_VALUE = SdkSchema.memberBuilder("string", PreludeSchemas.STRING)
        .id(ID)
        .traits()
        .build();

    SdkSchema SCHEMA_INTEGER_VALUE = SdkSchema.memberBuilder("integer", PreludeSchemas.INTEGER)
        .id(ID)
        .traits()
        .build();

    SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.UNION)
        .members(
            SCHEMA_STRING_VALUE,
            SCHEMA_INTEGER_VALUE
        )
        .build();

    enum Member {
        STRING_VALUE,
        INTEGER_VALUE,
        $UNKNOWN
    }

    Member type();

    default String stringValue() {
        return null;
    }

    default Integer integerValue() {
        return null;
    }

    default Document $unknownValue() {
        return null;
    }

    record StringValue(String value) implements ExampleUnion {

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_STRING_VALUE, value);
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        @Override
        public Member type() {
            return Member.STRING_VALUE;
        }

        @Override
        public String stringValue() {
            return value;
        }
    }

    record IntegerValue(int value) implements ExampleUnion {

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA_INTEGER_VALUE, value);
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        @Override
        public Member type() {
            return Member.INTEGER_VALUE;
        }

        @Override
        public Integer integerValue() {
            return value;
        }
    }

    record $Unknown(Document value) implements ExampleUnion {

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeDocument(value);
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        @Override
        public Member type() {
            return Member.$UNKNOWN;
        }

        @Override
        public Document $unknownValue() {
            return value;
        }
    }

    @Override
    default SdkSchema schema() {
        return SCHEMA;
    }

    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder
     */
    final class Builder implements SdkShapeBuilder<ExampleUnion> {

        private ExampleUnion value;

        private Builder() {}

        public Builder stringValue(String value) {
            checkForExistingValue();
            this.value = new StringValue(value);
            return this;
        }

        public Builder integerValue(int value) {
            checkForExistingValue();
            this.value = new IntegerValue(value);
            return this;
        }

        private void checkForExistingValue() {
            if (this.value != null) {
                throw new SdkSerdeException("Only one value may be set for a union.");
            }
        }

        @Override
        public SdkShapeBuilder<ExampleUnion> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        @Override
        public ExampleUnion build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        // Unknown variant should only ever be used in deser.
        // Should not be settable intentionally
        private void unknown(Document value) {
            checkForExistingValue();
            this.value = new $Unknown(value);
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.stringValue(de.readString(member));
                    case 1 -> builder.integerValue(de.readInteger(member));
                    // This doesn't actually work RN b/c if the member isn't found the deserializer just skips
                    default -> builder.unknown(de.readDocument());
                }
            }
        }
    }
}
