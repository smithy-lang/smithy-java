/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BodyAndHeaderEvent implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.BODY_AND_HEADER_EVENT;
    private static final Schema $SCHEMA_INT_MEMBER = $SCHEMA.member("intMember");
    private static final Schema $SCHEMA_STRING_MEMBER = $SCHEMA.member("stringMember");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient Integer intMember;
    private final transient String stringMember;

    private BodyAndHeaderEvent(Builder builder) {
        this.intMember = builder.intMember;
        this.stringMember = builder.stringMember;
    }

    public Integer getIntMember() {
        return intMember;
    }

    public String getStringMember() {
        return stringMember;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        BodyAndHeaderEvent that = (BodyAndHeaderEvent) other;
        return Objects.equals(this.intMember, that.intMember)
                && Objects.equals(this.stringMember, that.stringMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intMember, stringMember);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (intMember != null) {
            serializer.writeInteger($SCHEMA_INT_MEMBER, intMember);
        }
        if (stringMember != null) {
            serializer.writeString($SCHEMA_STRING_MEMBER, stringMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER, member, intMember);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_STRING_MEMBER, member, stringMember);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link BodyAndHeaderEvent}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.intMember(this.intMember);
        builder.stringMember(this.stringMember);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BodyAndHeaderEvent}.
     */
    public static final class Builder implements ShapeBuilder<BodyAndHeaderEvent> {
        private Integer intMember;
        private String stringMember;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder intMember(Integer intMember) {
            this.intMember = intMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder stringMember(String stringMember) {
            this.stringMember = stringMember;
            return this;
        }

        @Override
        public BodyAndHeaderEvent build() {
            return new BodyAndHeaderEvent(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> intMember((Integer) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER, member, value));
                case 1 -> stringMember((String) SchemaUtils.validateSameMember($SCHEMA_STRING_MEMBER, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct($SCHEMA, this, $InnerDeserializer.INSTANCE);
            return this;
        }

        @Override
        public Builder deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs($SCHEMA), this, $InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class $InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final $InnerDeserializer INSTANCE = new $InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.intMember(de.readInteger(member));
                    case 1 -> builder.stringMember(de.readString(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
