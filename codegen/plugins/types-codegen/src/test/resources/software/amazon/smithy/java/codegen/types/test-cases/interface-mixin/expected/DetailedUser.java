
package software.amazon.smithy.java.example.standalone.model;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.PresenceTracker;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * A concrete structure implementing a chained interface mixin hierarchy
 */
@SmithyGenerated
public final class DetailedUser implements SerializableStruct, HasFullName {

    public static final Schema $SCHEMA = Schemas.DETAILED_USER;
    private static final Schema $SCHEMA_NAME = $SCHEMA.member("name");
    private static final Schema $SCHEMA_ID = $SCHEMA.member("id");
    private static final Schema $SCHEMA_LAST_NAME = $SCHEMA.member("lastName");
    private static final Schema $SCHEMA_AGE = $SCHEMA.member("age");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String name;
    private final transient int id;
    private final transient String lastName;
    private final transient Integer age;

    private DetailedUser(Builder builder) {
        this.name = builder.name;
        this.id = builder.id;
        this.lastName = builder.lastName;
        this.age = builder.age;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    public Integer getAge() {
        return age;
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
        DetailedUser that = (DetailedUser) other;
        return Objects.equals(this.name, that.name)
               && this.id == that.id
               && Objects.equals(this.lastName, that.lastName)
               && Objects.equals(this.age, that.age);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, id, lastName, age);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (name != null) {
            serializer.writeString($SCHEMA_NAME, name);
        }
        serializer.writeInteger($SCHEMA_ID, id);
        if (lastName != null) {
            serializer.writeString($SCHEMA_LAST_NAME, lastName);
        }
        if (age != null) {
            serializer.writeInteger($SCHEMA_AGE, age);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_ID, member, id);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_NAME, member, name);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_LAST_NAME, member, lastName);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_AGE, member, age);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link DetailedUser}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.name(this.name);
        builder.id(this.id);
        builder.lastName(this.lastName);
        builder.age(this.age);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DetailedUser}.
     */
    public static final class Builder implements ShapeBuilder<DetailedUser> {
        private final PresenceTracker tracker = PresenceTracker.of($SCHEMA);
        private String name;
        private int id;
        private String lastName;
        private Integer age;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder id(int id) {
            this.id = id;
            tracker.setMember($SCHEMA_ID);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder lastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder age(Integer age) {
            this.age = age;
            return this;
        }

        @Override
        public DetailedUser build() {
            tracker.validate();
            return new DetailedUser(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> id((int) SchemaUtils.validateSameMember($SCHEMA_ID, member, value));
                case 1 -> name((String) SchemaUtils.validateSameMember($SCHEMA_NAME, member, value));
                case 2 -> lastName((String) SchemaUtils.validateSameMember($SCHEMA_LAST_NAME, member, value));
                case 3 -> age((Integer) SchemaUtils.validateSameMember($SCHEMA_AGE, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public ShapeBuilder<DetailedUser> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember($SCHEMA_ID)) {
                tracker.setMember($SCHEMA_ID);
            }
            return this;
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
                    case 0 -> builder.id(de.readInteger(member));
                    case 1 -> builder.name(de.readString(member));
                    case 2 -> builder.lastName(de.readString(member));
                    case 3 -> builder.age(de.readInteger(member));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}

