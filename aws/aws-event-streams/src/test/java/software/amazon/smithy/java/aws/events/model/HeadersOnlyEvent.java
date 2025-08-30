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
public final class HeadersOnlyEvent implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.HEADERS_ONLY_EVENT;
    private static final Schema $SCHEMA_SEQUENCE_NUM = $SCHEMA.member("sequenceNum");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient Integer sequenceNum;

    private HeadersOnlyEvent(Builder builder) {
        this.sequenceNum = builder.sequenceNum;
    }

    public Integer getSequenceNum() {
        return sequenceNum;
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
        HeadersOnlyEvent that = (HeadersOnlyEvent) other;
        return Objects.equals(this.sequenceNum, that.sequenceNum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNum);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (sequenceNum != null) {
            serializer.writeInteger($SCHEMA_SEQUENCE_NUM, sequenceNum);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_SEQUENCE_NUM, member, sequenceNum);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link HeadersOnlyEvent}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.sequenceNum(this.sequenceNum);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link HeadersOnlyEvent}.
     */
    public static final class Builder implements ShapeBuilder<HeadersOnlyEvent> {
        private Integer sequenceNum;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder sequenceNum(Integer sequenceNum) {
            this.sequenceNum = sequenceNum;
            return this;
        }

        @Override
        public HeadersOnlyEvent build() {
            return new HeadersOnlyEvent(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> sequenceNum((Integer) SchemaUtils.validateSameMember($SCHEMA_SEQUENCE_NUM, member, value));
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
                    case 0 -> builder.sequenceNum(de.readInteger(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
