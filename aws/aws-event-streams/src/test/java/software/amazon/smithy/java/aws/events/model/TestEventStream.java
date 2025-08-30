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
public abstract class TestEventStream implements SerializableStruct {
    public static final Schema $SCHEMA = Schemas.TEST_EVENT_STREAM;
    private static final Schema $SCHEMA_STRUCTURE_MEMBER = $SCHEMA.member("structureMember");
    private static final Schema $SCHEMA_STRING_MEMBER = $SCHEMA.member("stringMember");
    private static final Schema $SCHEMA_BLOB_MEMBER = $SCHEMA.member("blobMember");
    private static final Schema $SCHEMA_HEADERS_ONLY_MEMBER = $SCHEMA.member("headersOnlyMember");
    private static final Schema $SCHEMA_BODY_AND_HEADER_MEMBER = $SCHEMA.member("bodyAndHeaderMember");

    public static final ShapeId $ID = $SCHEMA.id();

    private final Type type;

    private TestEventStream(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    /**
     * Enum representing the possible variants of {@link TestEventStream}.
     */
    public enum Type {
        $UNKNOWN,
        structureMember,
        stringMember,
        blobMember,
        headersOnlyMember,
        bodyAndHeaderMember
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public <T> T getMemberValue(Schema member) {
        return SchemaUtils.validateMemberInSchema($SCHEMA, member, getValue());
    }

    public abstract <T> T getValue();

    @SmithyGenerated
    public static final class StructureMemberMember extends TestEventStream {
        private final transient StructureEvent value;

        public StructureMemberMember(StructureEvent value) {
            super(Type.structureMember);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_STRUCTURE_MEMBER, value);
        }

        public StructureEvent getStructureMember() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }

    @SmithyGenerated
    public static final class StringMemberMember extends TestEventStream {
        private final transient StringEvent value;

        public StringMemberMember(StringEvent value) {
            super(Type.stringMember);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_STRING_MEMBER, value);
        }

        public StringEvent getStringMember() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }

    @SmithyGenerated
    public static final class BlobMemberMember extends TestEventStream {
        private final transient BlobEvent value;

        public BlobMemberMember(BlobEvent value) {
            super(Type.blobMember);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_BLOB_MEMBER, value);
        }

        public BlobEvent getBlobMember() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }

    @SmithyGenerated
    public static final class HeadersOnlyMemberMember extends TestEventStream {
        private final transient HeadersOnlyEvent value;

        public HeadersOnlyMemberMember(HeadersOnlyEvent value) {
            super(Type.headersOnlyMember);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_HEADERS_ONLY_MEMBER, value);
        }

        public HeadersOnlyEvent getHeadersOnlyMember() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }

    @SmithyGenerated
    public static final class BodyAndHeaderMemberMember extends TestEventStream {
        private final transient BodyAndHeaderEvent value;

        public BodyAndHeaderMemberMember(BodyAndHeaderEvent value) {
            super(Type.bodyAndHeaderMember);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct($SCHEMA_BODY_AND_HEADER_MEMBER, value);
        }

        public BodyAndHeaderEvent getBodyAndHeaderMember() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) value;
        }
    }

    public static final class $UnknownMember extends TestEventStream {
        private final String memberName;

        public $UnknownMember(String memberName) {
            super(Type.$UNKNOWN);
            this.memberName = memberName;
        }

        public String memberName() {
            return memberName;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            throw new UnsupportedOperationException("Cannot serialize union with unknown member " + this.memberName);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getValue() {
            return (T) memberName;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, getValue());
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        return Objects.equals(getValue(), ((TestEventStream) other).getValue());
    }

    public interface BuildStage {
        TestEventStream build();
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TestEventStream}.
     */
    public static final class Builder implements ShapeBuilder<TestEventStream>, BuildStage {
        private TestEventStream value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        public BuildStage structureMember(StructureEvent value) {
            return setValue(new StructureMemberMember(value));
        }

        public BuildStage stringMember(StringEvent value) {
            return setValue(new StringMemberMember(value));
        }

        public BuildStage blobMember(BlobEvent value) {
            return setValue(new BlobMemberMember(value));
        }

        public BuildStage headersOnlyMember(HeadersOnlyEvent value) {
            return setValue(new HeadersOnlyMemberMember(value));
        }

        public BuildStage bodyAndHeaderMember(BodyAndHeaderEvent value) {
            return setValue(new BodyAndHeaderMemberMember(value));
        }

        public BuildStage $unknownMember(String memberName) {
            return setValue(new $UnknownMember(memberName));
        }

        private BuildStage setValue(TestEventStream value) {
            if (this.value != null) {
                if (this.value.type() == Type.$UNKNOWN) {
                    throw new IllegalArgumentException("Cannot change union from unknown to known variant");
                }
                throw new IllegalArgumentException("Only one value may be set for unions");
            }
            this.value = value;
            return this;
        }

        @Override
        public TestEventStream build() {
            return Objects.requireNonNull(value, "no union value set");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> structureMember(
                        (StructureEvent) SchemaUtils.validateSameMember($SCHEMA_STRUCTURE_MEMBER, member, value));
                case 1 ->
                    stringMember((StringEvent) SchemaUtils.validateSameMember($SCHEMA_STRING_MEMBER, member, value));
                case 2 -> blobMember((BlobEvent) SchemaUtils.validateSameMember($SCHEMA_BLOB_MEMBER, member, value));
                case 3 -> headersOnlyMember(
                        (HeadersOnlyEvent) SchemaUtils.validateSameMember($SCHEMA_HEADERS_ONLY_MEMBER, member, value));
                case 4 -> bodyAndHeaderMember((BodyAndHeaderEvent) SchemaUtils
                        .validateSameMember($SCHEMA_BODY_AND_HEADER_MEMBER, member, value));
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
                    case 0 -> builder.structureMember(StructureEvent.builder().deserializeMember(de, member).build());
                    case 1 -> builder.stringMember(StringEvent.builder().deserializeMember(de, member).build());
                    case 2 -> builder.blobMember(BlobEvent.builder().deserializeMember(de, member).build());
                    case 3 ->
                        builder.headersOnlyMember(HeadersOnlyEvent.builder().deserializeMember(de, member).build());
                    case 4 ->
                        builder.bodyAndHeaderMember(BodyAndHeaderEvent.builder().deserializeMember(de, member).build());
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }

            @Override
            public void unknownMember(Builder builder, String memberName) {
                builder.$unknownMember(memberName);
            }
        }
    }
}
