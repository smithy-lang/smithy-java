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
import software.amazon.smithy.java.core.serde.event.EventStream;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class TestOperationOutput implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.TEST_OPERATION_OUTPUT;
    private static final Schema $SCHEMA_INT_MEMBER_HEADER = $SCHEMA.member("intMemberHeader");
    private static final Schema $SCHEMA_STRING_MEMBER = $SCHEMA.member("stringMember");
    private static final Schema $SCHEMA_OUTPUT_STREAM = $SCHEMA.member("outputStream");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient Integer intMemberHeader;
    private final transient String stringMember;
    private final transient EventStream<TestEventStream> outputStream;

    private TestOperationOutput(Builder builder) {
        this.intMemberHeader = builder.intMemberHeader;
        this.stringMember = builder.stringMember;
        this.outputStream = builder.outputStream;
    }

    public Integer getIntMemberHeader() {
        return intMemberHeader;
    }

    public String getStringMember() {
        return stringMember;
    }

    public EventStream<TestEventStream> getOutputStream() {
        return outputStream;
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
        TestOperationOutput that = (TestOperationOutput) other;
        return Objects.equals(this.intMemberHeader, that.intMemberHeader)
                && Objects.equals(this.stringMember, that.stringMember)
                && Objects.equals(this.outputStream, that.outputStream);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intMemberHeader, stringMember, outputStream);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (intMemberHeader != null) {
            serializer.writeInteger($SCHEMA_INT_MEMBER_HEADER, intMemberHeader);
        }
        if (stringMember != null) {
            serializer.writeString($SCHEMA_STRING_MEMBER, stringMember);
        }
        if (outputStream != null) {
            serializer.writeEventStream($SCHEMA_OUTPUT_STREAM, outputStream);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER_HEADER, member, intMemberHeader);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_STRING_MEMBER, member, stringMember);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_OUTPUT_STREAM, member, outputStream);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link TestOperationOutput}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.intMemberHeader(this.intMemberHeader);
        builder.stringMember(this.stringMember);
        builder.outputStream(this.outputStream);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TestOperationOutput}.
     */
    public static final class Builder implements ShapeBuilder<TestOperationOutput> {
        private Integer intMemberHeader;
        private String stringMember;
        private EventStream<TestEventStream> outputStream;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder intMemberHeader(Integer intMemberHeader) {
            this.intMemberHeader = intMemberHeader;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder stringMember(String stringMember) {
            this.stringMember = stringMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder outputStream(EventStream<TestEventStream> outputStream) {
            this.outputStream = outputStream;
            return this;
        }

        @Override
        public TestOperationOutput build() {
            return new TestOperationOutput(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 ->
                    intMemberHeader((Integer) SchemaUtils.validateSameMember($SCHEMA_INT_MEMBER_HEADER, member, value));
                case 1 -> stringMember((String) SchemaUtils.validateSameMember($SCHEMA_STRING_MEMBER, member, value));
                case 2 -> outputStream((EventStream<TestEventStream>) SchemaUtils
                        .validateSameMember($SCHEMA_OUTPUT_STREAM, member, value));
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
                    case 0 -> builder.intMemberHeader(de.readInteger(member));
                    case 1 -> builder.stringMember(de.readString(member));
                    case 2 -> builder.outputStream((EventStream<TestEventStream>) de.readEventStream(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
