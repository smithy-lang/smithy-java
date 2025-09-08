/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import java.util.Objects;
import java.util.concurrent.Flow.Publisher;
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
public final class TestOperationInput implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.TEST_OPERATION_INPUT;
    private static final Schema $SCHEMA_HEADER_STRING = $SCHEMA.member("headerString");
    private static final Schema $SCHEMA_INPUT_STRING_MEMBER = $SCHEMA.member("inputStringMember");
    private static final Schema $SCHEMA_STREAM = $SCHEMA.member("stream");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String headerString;
    private final transient String inputStringMember;
    private final transient Publisher<TestEventStream> stream;

    private TestOperationInput(Builder builder) {
        this.headerString = builder.headerString;
        this.inputStringMember = builder.inputStringMember;
        this.stream = builder.stream;
    }

    public String getHeaderString() {
        return headerString;
    }

    public String getInputStringMember() {
        return inputStringMember;
    }

    public Publisher<TestEventStream> getStream() {
        return stream;
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
        TestOperationInput that = (TestOperationInput) other;
        return Objects.equals(this.headerString, that.headerString)
                && Objects.equals(this.inputStringMember, that.inputStringMember)
                && Objects.equals(this.stream, that.stream);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headerString, inputStringMember, stream);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (headerString != null) {
            serializer.writeString($SCHEMA_HEADER_STRING, headerString);
        }
        if (inputStringMember != null) {
            serializer.writeString($SCHEMA_INPUT_STRING_MEMBER, inputStringMember);
        }
        if (stream != null) {
            serializer.writeEventStream($SCHEMA_STREAM, stream);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_HEADER_STRING, member, headerString);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_INPUT_STRING_MEMBER, member, inputStringMember);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_STREAM, member, stream);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link TestOperationInput}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.headerString(this.headerString);
        builder.inputStringMember(this.inputStringMember);
        builder.stream(this.stream);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TestOperationInput}.
     */
    public static final class Builder implements ShapeBuilder<TestOperationInput> {
        private String headerString;
        private String inputStringMember;
        private Publisher<TestEventStream> stream;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder headerString(String headerString) {
            this.headerString = headerString;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder inputStringMember(String inputStringMember) {
            this.inputStringMember = inputStringMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder stream(Publisher<TestEventStream> stream) {
            this.stream = stream;
            return this;
        }

        @Override
        public TestOperationInput build() {
            return new TestOperationInput(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> headerString((String) SchemaUtils.validateSameMember($SCHEMA_HEADER_STRING, member, value));
                case 1 -> inputStringMember(
                        (String) SchemaUtils.validateSameMember($SCHEMA_INPUT_STRING_MEMBER, member, value));
                case 2 ->
                    stream((Publisher<TestEventStream>) SchemaUtils.validateSameMember($SCHEMA_STREAM, member, value));
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
                    case 0 -> builder.headerString(de.readString(member));
                    case 1 -> builder.inputStringMember(de.readString(member));
                    case 2 -> builder.stream((Publisher<TestEventStream>) de.readEventStream(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
