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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Structure with required, optional, primitive, and sparse collection members
 */
@SmithyGenerated
@NullMarked
public final class JSpecifyStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.J_SPECIFY_STRUCT;
    private static final Schema $0 = $SCHEMA.member(0); // requiredString
    private static final Schema $1 = $SCHEMA.member(1); // requiredPrimitive
    private static final Schema $2 = $SCHEMA.member(2); // optionalString
    private static final Schema $3 = $SCHEMA.member(3); // sparseList

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
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof JSpecifyStruct that)) {
            return false;
        }
        return this.requiredPrimitive == that.requiredPrimitive
               && Objects.equals(this.requiredString, that.requiredString)
               && Objects.equals(this.optionalString, that.optionalString)
               && Objects.equals(this.sparseList, that.sparseList);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(requiredString);
        $hc = 31 * $hc + Objects.hashCode(optionalString);
        $hc = 31 * $hc + Boolean.hashCode(requiredPrimitive);
        $hc = 31 * $hc + Objects.hashCode(sparseList);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString($0, requiredString);
        serializer.writeBoolean($1, requiredPrimitive);
        if (optionalString != null) {
            serializer.writeString($2, optionalString);
        }
        if (sparseList != null) {
            serializer.writeSparseStringList($3, sparseList, $3.listMember());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) requiredString;
            case 1 -> (T) (Object) requiredPrimitive;
            case 2 -> (T) optionalString;
            case 3 -> (T) sparseList;
            default -> throw SchemaUtils.unknownMember(member);
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
        private long $setMembers;
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
            this.requiredString = SchemaUtils.requireNonNull(requiredString, $0);
            $setMembers |= 0x1L;
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
            $setMembers |= 0x2L;
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
            if ($setMembers != 0x3L) {
                PresenceTracker.validateRequiredMembers($SCHEMA, $setMembers);
            }
            return new JSpecifyStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> requiredString((String) value);
                case 1 -> requiredPrimitive((boolean) value);
                case 2 -> optionalString((String) value);
                case 3 -> sparseList((List<@Nullable String>) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public ShapeBuilder<JSpecifyStruct> errorCorrection() {
            if ($setMembers == 0x3L) {
                return this;
            }
            if (($setMembers & 0x1L) == 0L) {
                requiredString("");
            }
            if (($setMembers & 0x2L) == 0L) {
                $setMembers |= 0x2L;
            }
            return this;
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
                case 0 -> builder.requiredString(de.readString(member));
                case 1 -> builder.requiredPrimitive(de.readBoolean(member));
                case 2 -> builder.optionalString(de.readString(member));
                case 3 -> builder.sparseList(SharedSerde.deserializeSparseStringList(member, de));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
