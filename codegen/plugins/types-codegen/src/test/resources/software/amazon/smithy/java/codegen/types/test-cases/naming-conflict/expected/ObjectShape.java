
package software.amazon.smithy.java.example.standalone.model;

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
public final class ObjectShape implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.OBJECT;
    private static final Schema $SCHEMA_CLASS_MEMBER = $SCHEMA.member("class");
    private static final Schema $SCHEMA_GET_CLASS_MEMBER = $SCHEMA.member("getClass");
    private static final Schema $SCHEMA_HASH_CODE_MEMBER = $SCHEMA.member("hashCode");
    private static final Schema $SCHEMA_CLONE_MEMBER = $SCHEMA.member("clone");
    private static final Schema $SCHEMA_TO_STRING_MEMBER = $SCHEMA.member("toString");
    private static final Schema $SCHEMA_NOTIFY_MEMBER = $SCHEMA.member("notify");
    private static final Schema $SCHEMA_NOTIFY_ALL_MEMBER = $SCHEMA.member("notifyAll");
    private static final Schema $SCHEMA_WAIT_MEMBER = $SCHEMA.member("wait");
    private static final Schema $SCHEMA_FINALIZE_MEMBER = $SCHEMA.member("finalize");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String classMember;
    private final transient String getClassMember;
    private final transient String hashCodeMember;
    private final transient String cloneMember;
    private final transient String toStringMember;
    private final transient String notifyMember;
    private final transient String notifyAllMember;
    private final transient String waitMember;
    private final transient String finalizeMember;

    private ObjectShape(Builder builder) {
        this.classMember = builder.classMember;
        this.getClassMember = builder.getClassMember;
        this.hashCodeMember = builder.hashCodeMember;
        this.cloneMember = builder.cloneMember;
        this.toStringMember = builder.toStringMember;
        this.notifyMember = builder.notifyMember;
        this.notifyAllMember = builder.notifyAllMember;
        this.waitMember = builder.waitMember;
        this.finalizeMember = builder.finalizeMember;
    }

    public String getClassMember() {
        return classMember;
    }

    public String getGetClass() {
        return getClassMember;
    }

    public String getHashCode() {
        return hashCodeMember;
    }

    public String getClone() {
        return cloneMember;
    }

    public String getToString() {
        return toStringMember;
    }

    public String getNotify() {
        return notifyMember;
    }

    public String getNotifyAll() {
        return notifyAllMember;
    }

    public String getWait() {
        return waitMember;
    }

    public String getFinalize() {
        return finalizeMember;
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
        ObjectShape that = (ObjectShape) other;
        return Objects.equals(this.classMember, that.classMember)
               && Objects.equals(this.getClassMember, that.getClassMember)
               && Objects.equals(this.hashCodeMember, that.hashCodeMember)
               && Objects.equals(this.cloneMember, that.cloneMember)
               && Objects.equals(this.toStringMember, that.toStringMember)
               && Objects.equals(this.notifyMember, that.notifyMember)
               && Objects.equals(this.notifyAllMember, that.notifyAllMember)
               && Objects.equals(this.waitMember, that.waitMember)
               && Objects.equals(this.finalizeMember, that.finalizeMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classMember, getClassMember, hashCodeMember, cloneMember, toStringMember, notifyMember, notifyAllMember, waitMember, finalizeMember);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (classMember != null) {
            serializer.writeString($SCHEMA_CLASS_MEMBER, classMember);
        }
        if (getClassMember != null) {
            serializer.writeString($SCHEMA_GET_CLASS_MEMBER, getClassMember);
        }
        if (hashCodeMember != null) {
            serializer.writeString($SCHEMA_HASH_CODE_MEMBER, hashCodeMember);
        }
        if (cloneMember != null) {
            serializer.writeString($SCHEMA_CLONE_MEMBER, cloneMember);
        }
        if (toStringMember != null) {
            serializer.writeString($SCHEMA_TO_STRING_MEMBER, toStringMember);
        }
        if (notifyMember != null) {
            serializer.writeString($SCHEMA_NOTIFY_MEMBER, notifyMember);
        }
        if (notifyAllMember != null) {
            serializer.writeString($SCHEMA_NOTIFY_ALL_MEMBER, notifyAllMember);
        }
        if (waitMember != null) {
            serializer.writeString($SCHEMA_WAIT_MEMBER, waitMember);
        }
        if (finalizeMember != null) {
            serializer.writeString($SCHEMA_FINALIZE_MEMBER, finalizeMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_CLASS_MEMBER, member, classMember);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_GET_CLASS_MEMBER, member, getClassMember);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_HASH_CODE_MEMBER, member, hashCodeMember);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_CLONE_MEMBER, member, cloneMember);
            case 4 -> (T) SchemaUtils.validateSameMember($SCHEMA_TO_STRING_MEMBER, member, toStringMember);
            case 5 -> (T) SchemaUtils.validateSameMember($SCHEMA_NOTIFY_MEMBER, member, notifyMember);
            case 6 -> (T) SchemaUtils.validateSameMember($SCHEMA_NOTIFY_ALL_MEMBER, member, notifyAllMember);
            case 7 -> (T) SchemaUtils.validateSameMember($SCHEMA_WAIT_MEMBER, member, waitMember);
            case 8 -> (T) SchemaUtils.validateSameMember($SCHEMA_FINALIZE_MEMBER, member, finalizeMember);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link ObjectShape}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.classMember(this.classMember);
        builder.getClassMember(this.getClassMember);
        builder.hashCodeMember(this.hashCodeMember);
        builder.cloneMember(this.cloneMember);
        builder.toStringMember(this.toStringMember);
        builder.notifyMember(this.notifyMember);
        builder.notifyAllMember(this.notifyAllMember);
        builder.waitMember(this.waitMember);
        builder.finalizeMember(this.finalizeMember);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ObjectShape}.
     */
    public static final class Builder implements ShapeBuilder<ObjectShape> {
        private String classMember;
        private String getClassMember;
        private String hashCodeMember;
        private String cloneMember;
        private String toStringMember;
        private String notifyMember;
        private String notifyAllMember;
        private String waitMember;
        private String finalizeMember;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder classMember(String classMember) {
            this.classMember = classMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder getClassMember(String getClassMember) {
            this.getClassMember = getClassMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder hashCodeMember(String hashCodeMember) {
            this.hashCodeMember = hashCodeMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder cloneMember(String cloneMember) {
            this.cloneMember = cloneMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder toStringMember(String toStringMember) {
            this.toStringMember = toStringMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder notifyMember(String notifyMember) {
            this.notifyMember = notifyMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder notifyAllMember(String notifyAllMember) {
            this.notifyAllMember = notifyAllMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder waitMember(String waitMember) {
            this.waitMember = waitMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder finalizeMember(String finalizeMember) {
            this.finalizeMember = finalizeMember;
            return this;
        }

        @Override
        public ObjectShape build() {
            return new ObjectShape(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> classMember((String) SchemaUtils.validateSameMember($SCHEMA_CLASS_MEMBER, member, value));
                case 1 -> getClassMember((String) SchemaUtils.validateSameMember($SCHEMA_GET_CLASS_MEMBER, member, value));
                case 2 -> hashCodeMember((String) SchemaUtils.validateSameMember($SCHEMA_HASH_CODE_MEMBER, member, value));
                case 3 -> cloneMember((String) SchemaUtils.validateSameMember($SCHEMA_CLONE_MEMBER, member, value));
                case 4 -> toStringMember((String) SchemaUtils.validateSameMember($SCHEMA_TO_STRING_MEMBER, member, value));
                case 5 -> notifyMember((String) SchemaUtils.validateSameMember($SCHEMA_NOTIFY_MEMBER, member, value));
                case 6 -> notifyAllMember((String) SchemaUtils.validateSameMember($SCHEMA_NOTIFY_ALL_MEMBER, member, value));
                case 7 -> waitMember((String) SchemaUtils.validateSameMember($SCHEMA_WAIT_MEMBER, member, value));
                case 8 -> finalizeMember((String) SchemaUtils.validateSameMember($SCHEMA_FINALIZE_MEMBER, member, value));
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
            @SuppressWarnings("unchecked")
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.classMember(de.readString(member));
                    case 1 -> builder.getClassMember(de.readString(member));
                    case 2 -> builder.hashCodeMember(de.readString(member));
                    case 3 -> builder.cloneMember(de.readString(member));
                    case 4 -> builder.toStringMember(de.readString(member));
                    case 5 -> builder.notifyMember(de.readString(member));
                    case 6 -> builder.notifyAllMember(de.readString(member));
                    case 7 -> builder.waitMember(de.readString(member));
                    case 8 -> builder.finalizeMember(de.readString(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}

