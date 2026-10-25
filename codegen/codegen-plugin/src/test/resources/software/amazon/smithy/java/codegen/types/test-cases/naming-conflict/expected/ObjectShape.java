package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ObjectShape implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.OBJECT;
    private static final Schema $0 = $SCHEMA.member(0); // class
    private static final Schema $1 = $SCHEMA.member(1); // getClass
    private static final Schema $2 = $SCHEMA.member(2); // hashCode
    private static final Schema $3 = $SCHEMA.member(3); // clone
    private static final Schema $4 = $SCHEMA.member(4); // toString
    private static final Schema $5 = $SCHEMA.member(5); // notify
    private static final Schema $6 = $SCHEMA.member(6); // notifyAll
    private static final Schema $7 = $SCHEMA.member(7); // wait
    private static final Schema $8 = $SCHEMA.member(8); // finalize

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
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof ObjectShape that)) {
            return false;
        }
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
        int $hc = Objects.hashCode(classMember);
        $hc = 31 * $hc + Objects.hashCode(getClassMember);
        $hc = 31 * $hc + Objects.hashCode(hashCodeMember);
        $hc = 31 * $hc + Objects.hashCode(cloneMember);
        $hc = 31 * $hc + Objects.hashCode(toStringMember);
        $hc = 31 * $hc + Objects.hashCode(notifyMember);
        $hc = 31 * $hc + Objects.hashCode(notifyAllMember);
        $hc = 31 * $hc + Objects.hashCode(waitMember);
        $hc = 31 * $hc + Objects.hashCode(finalizeMember);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (classMember != null) {
            serializer.writeString($0, classMember);
        }
        if (getClassMember != null) {
            serializer.writeString($1, getClassMember);
        }
        if (hashCodeMember != null) {
            serializer.writeString($2, hashCodeMember);
        }
        if (cloneMember != null) {
            serializer.writeString($3, cloneMember);
        }
        if (toStringMember != null) {
            serializer.writeString($4, toStringMember);
        }
        if (notifyMember != null) {
            serializer.writeString($5, notifyMember);
        }
        if (notifyAllMember != null) {
            serializer.writeString($6, notifyAllMember);
        }
        if (waitMember != null) {
            serializer.writeString($7, waitMember);
        }
        if (finalizeMember != null) {
            serializer.writeString($8, finalizeMember);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) classMember;
            case 1 -> (T) getClassMember;
            case 2 -> (T) hashCodeMember;
            case 3 -> (T) cloneMember;
            case 4 -> (T) toStringMember;
            case 5 -> (T) notifyMember;
            case 6 -> (T) notifyAllMember;
            case 7 -> (T) waitMember;
            case 8 -> (T) finalizeMember;
            default -> throw SchemaUtils.unknownMember(member);
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
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> classMember((String) value);
                case 1 -> getClassMember((String) value);
                case 2 -> hashCodeMember((String) value);
                case 3 -> cloneMember((String) value);
                case 4 -> toStringMember((String) value);
                case 5 -> notifyMember((String) value);
                case 6 -> notifyAllMember((String) value);
                case 7 -> waitMember((String) value);
                case 8 -> finalizeMember((String) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct($SCHEMA, this, Builder::$deserializeMember);
            return this;
        }

        @Override
        public Builder deserializeMember(ShapeDeserializer decoder, Schema schema) {
            decoder.readStruct(schema.assertMemberTargetIs($SCHEMA), this, Builder::$deserializeMember);
            return this;
        }

        private static void $deserializeMember(
                Builder builder,
                Schema member,
                ShapeDeserializer de
        ) {
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
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
