package software.amazon.smithy.java.example.standalone.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
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
 * Structure with required, optional, primitive, and sparse collection members
 */
@SmithyGenerated
@NullMarked
public final class JSpecifyStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.J_SPECIFY_STRUCT;
    private static final Schema $SCHEMA_REQUIRED_STRING = $SCHEMA.member("requiredString");
    private static final Schema $SCHEMA_OPTIONAL_STRING = $SCHEMA.member("optionalString");
    private static final Schema $SCHEMA_REQUIRED_PRIMITIVE = $SCHEMA.member("requiredPrimitive");
    private static final Schema $SCHEMA_SPARSE_LIST = $SCHEMA.member("sparseList");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String requiredString;
    private final transient String optionalString;
    private final transient boolean requiredPrimitive;
    private final transient List<@Nullable String> sparseList;

    private JSpecifyStruct(Builder builder) {
        this.requiredString = builder.requiredString;
        this.optionalString = builder.optionalString;
        this.requiredPrimitive = builder.requiredPrimitive;
        this.sparseList = builder.sparseList == null ? null : Collections.unmodifiableList(builder.sparseList);
    }

    public String getRequiredString() {
        return requiredString;
    }

    public @Nullable String getOptionalString() {
        return optionalString;
    }

    public boolean isRequiredPrimitive() {
        return requiredPrimitive;
    }

    public List<@Nullable String> getSparseList() {
        if (sparseList == null) {
            return Collections.emptyList();
        }
        return sparseList;
    }

    public boolean hasSparseList() {
        return sparseList != null;
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
        JSpecifyStruct that = (JSpecifyStruct) other;
        return Objects.equals(this.requiredString, that.requiredString)
               && Objects.equals(this.optionalString, that.optionalString)
               && this.requiredPrimitive == that.requiredPrimitive
               && Objects.equals(this.sparseList, that.sparseList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredString, optionalString, requiredPrimitive, sparseList);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString($SCHEMA_REQUIRED_STRING, requiredString);
        if (optionalString != null) {
            serializer.writeString($SCHEMA_OPTIONAL_STRING, optionalString);
        }
        serializer.writeBoolean($SCHEMA_REQUIRED_PRIMITIVE, requiredPrimitive);
        if (sparseList != null) {
            serializer.writeList($SCHEMA_SPARSE_LIST, sparseList, sparseList.size(), SharedSerde.SparseStringListSerializer.INSTANCE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_REQUIRED_STRING, member, requiredString);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_REQUIRED_PRIMITIVE, member, requiredPrimitive);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_OPTIONAL_STRING, member, optionalString);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, sparseList);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link JSpecifyStruct}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.requiredString(this.requiredString);
        builder.optionalString(this.optionalString);
        builder.requiredPrimitive(this.requiredPrimitive);
        builder.sparseList(this.sparseList);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link JSpecifyStruct}.
     */
    public static final class Builder implements ShapeBuilder<JSpecifyStruct> {
        private final PresenceTracker tracker = PresenceTracker.of($SCHEMA);
        private String requiredString;
        private String optionalString;
        private boolean requiredPrimitive;
        private List<@Nullable String> sparseList;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder requiredString(String requiredString) {
            this.requiredString = Objects.requireNonNull(requiredString, "requiredString cannot be null");
            tracker.setMember($SCHEMA_REQUIRED_STRING);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder optionalString(@Nullable String optionalString) {
            this.optionalString = optionalString;
            return this;
        }

        /**
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder requiredPrimitive(boolean requiredPrimitive) {
            this.requiredPrimitive = requiredPrimitive;
            tracker.setMember($SCHEMA_REQUIRED_PRIMITIVE);
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder sparseList(@Nullable List<@Nullable String> sparseList) {
            this.sparseList = sparseList;
            return this;
        }

        @Override
        public JSpecifyStruct build() {
            tracker.validate();
            return new JSpecifyStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> requiredString((String) SchemaUtils.validateSameMember($SCHEMA_REQUIRED_STRING, member, value));
                case 1 -> requiredPrimitive((boolean) SchemaUtils.validateSameMember($SCHEMA_REQUIRED_PRIMITIVE, member, value));
                case 2 -> optionalString((String) SchemaUtils.validateSameMember($SCHEMA_OPTIONAL_STRING, member, value));
                case 3 -> sparseList((List<@Nullable String>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public ShapeBuilder<JSpecifyStruct> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember($SCHEMA_REQUIRED_STRING)) {
                requiredString("");
            }
            if (!tracker.checkMember($SCHEMA_REQUIRED_PRIMITIVE)) {
                tracker.setMember($SCHEMA_REQUIRED_PRIMITIVE);
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
            @SuppressWarnings("unchecked")
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredString(de.readString(member));
                    case 1 -> builder.requiredPrimitive(de.readBoolean(member));
                    case 2 -> builder.optionalString(de.readString(member));
                    case 3 -> builder.sparseList(SharedSerde.deserializeSparseStringList(member, de));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
