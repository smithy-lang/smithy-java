package software.amazon.smithy.java.example.standalone.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * Structure exercising all collection sparsity combinations
 */
@SmithyGenerated
@NullMarked
public final class CollectionStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.COLLECTION_STRUCT;
    private static final Schema $0 = $SCHEMA.member(0); // sparseList
    private static final Schema $1 = $SCHEMA.member(1); // sparseMap
    private static final Schema $2 = $SCHEMA.member(2); // sparseListOfSparseMap
    private static final Schema $3 = $SCHEMA.member(3); // nonSparseListOfNonSparseMap
    private static final Schema $4 = $SCHEMA.member(4); // nonSparseList
    private static final Schema $5 = $SCHEMA.member(5); // nonSparseMap
    private static final Schema $6 = $SCHEMA.member(6); // nonSparseListOfSparseMap
    private static final Schema $7 = $SCHEMA.member(7); // sparseMapOfNonSparseList

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient List<String> nonSparseList;
    private final transient List<@Nullable String> sparseList;
    private final transient Map<String, String> nonSparseMap;
    private final transient Map<String, @Nullable String> sparseMap;
    private final transient List<Map<String, @Nullable String>> nonSparseListOfSparseMap;
    private final transient List<@Nullable Map<String, @Nullable String>> sparseListOfSparseMap;
    private final transient Map<String, @Nullable List<String>> sparseMapOfNonSparseList;
    private final transient List<Map<String, String>> nonSparseListOfNonSparseMap;

    private CollectionStruct(Builder builder) {
        this.nonSparseList = builder.nonSparseList == null ? null : Collections.unmodifiableList(builder.nonSparseList);
        this.sparseList = Collections.unmodifiableList(builder.sparseList);
        this.nonSparseMap = builder.nonSparseMap == null ? null : Collections.unmodifiableMap(builder.nonSparseMap);
        this.sparseMap = Collections.unmodifiableMap(builder.sparseMap);
        this.nonSparseListOfSparseMap = builder.nonSparseListOfSparseMap == null ? null : Collections.unmodifiableList(builder.nonSparseListOfSparseMap);
        this.sparseListOfSparseMap = Collections.unmodifiableList(builder.sparseListOfSparseMap);
        this.sparseMapOfNonSparseList = builder.sparseMapOfNonSparseList == null ? null : Collections.unmodifiableMap(builder.sparseMapOfNonSparseList);
        this.nonSparseListOfNonSparseMap = Collections.unmodifiableList(builder.nonSparseListOfNonSparseMap);
    }

    /**
     * Non-sparse list: List&lt;String&gt;
     */
    public List<String> getNonSparseList() {
        if (nonSparseList == null) {
            return Collections.emptyList();
        }
        return nonSparseList;
    }

    public boolean hasNonSparseList() {
        return nonSparseList != null;
    }

    /**
     * Sparse list: List&lt;{@literal @}Nullable String&gt;
     */
    public List<@Nullable String> getSparseList() {
        return sparseList;
    }

    public boolean hasSparseList() {
        return true;
    }

    /**
     * Non-sparse map: Map&lt;String, String&gt;
     */
    public Map<String, String> getNonSparseMap() {
        if (nonSparseMap == null) {
            return Collections.emptyMap();
        }
        return nonSparseMap;
    }

    public boolean hasNonSparseMap() {
        return nonSparseMap != null;
    }

    /**
     * Sparse map: Map&lt;String, {@literal @}Nullable String&gt;
     */
    public Map<String, @Nullable String> getSparseMap() {
        return sparseMap;
    }

    public boolean hasSparseMap() {
        return true;
    }

    /**
     * Non-sparse list of sparse map: List&lt;Map&lt;String, {@literal @}Nullable String&gt;&gt;
     */
    public List<Map<String, @Nullable String>> getNonSparseListOfSparseMap() {
        if (nonSparseListOfSparseMap == null) {
            return Collections.emptyList();
        }
        return nonSparseListOfSparseMap;
    }

    public boolean hasNonSparseListOfSparseMap() {
        return nonSparseListOfSparseMap != null;
    }

    /**
     * Sparse list of sparse map: List&lt;{@literal @}Nullable Map&lt;String, {@literal @}Nullable String&gt;&gt;
     */
    public List<@Nullable Map<String, @Nullable String>> getSparseListOfSparseMap() {
        return sparseListOfSparseMap;
    }

    public boolean hasSparseListOfSparseMap() {
        return true;
    }

    /**
     * Sparse map of non-sparse list: Map&lt;String, {@literal @}Nullable List&lt;String&gt;&gt;
     */
    public Map<String, @Nullable List<String>> getSparseMapOfNonSparseList() {
        if (sparseMapOfNonSparseList == null) {
            return Collections.emptyMap();
        }
        return sparseMapOfNonSparseList;
    }

    public boolean hasSparseMapOfNonSparseList() {
        return sparseMapOfNonSparseList != null;
    }

    /**
     * Non-sparse list of non-sparse map: List&lt;Map&lt;String, String&gt;&gt;
     */
    public List<Map<String, String>> getNonSparseListOfNonSparseMap() {
        return nonSparseListOfNonSparseMap;
    }

    public boolean hasNonSparseListOfNonSparseMap() {
        return true;
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
        if (!(other instanceof CollectionStruct that)) {
            return false;
        }
        return Objects.equals(this.nonSparseList, that.nonSparseList)
               && Objects.equals(this.sparseList, that.sparseList)
               && Objects.equals(this.nonSparseListOfSparseMap, that.nonSparseListOfSparseMap)
               && Objects.equals(this.sparseListOfSparseMap, that.sparseListOfSparseMap)
               && Objects.equals(this.nonSparseListOfNonSparseMap, that.nonSparseListOfNonSparseMap)
               && Objects.equals(this.nonSparseMap, that.nonSparseMap)
               && Objects.equals(this.sparseMap, that.sparseMap)
               && Objects.equals(this.sparseMapOfNonSparseList, that.sparseMapOfNonSparseList);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(nonSparseList);
        $hc = 31 * $hc + Objects.hashCode(sparseList);
        $hc = 31 * $hc + Objects.hashCode(nonSparseMap);
        $hc = 31 * $hc + Objects.hashCode(sparseMap);
        $hc = 31 * $hc + Objects.hashCode(nonSparseListOfSparseMap);
        $hc = 31 * $hc + Objects.hashCode(sparseListOfSparseMap);
        $hc = 31 * $hc + Objects.hashCode(sparseMapOfNonSparseList);
        $hc = 31 * $hc + Objects.hashCode(nonSparseListOfNonSparseMap);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeSparseStringList($0, sparseList, $0.listMember());
        serializer.writeSparseStringMap($1, sparseMap, $1.mapKeyMember(), $1.mapValueMember());
        serializer.writeList($2, sparseListOfSparseMap, sparseListOfSparseMap.size(), SharedSerde.SparseListOfSparseMapSerializer.INSTANCE);
        serializer.writeList($3, nonSparseListOfNonSparseMap, nonSparseListOfNonSparseMap.size(), SharedSerde.NonSparseListOfNonSparseMapSerializer.INSTANCE);
        if (nonSparseList != null) {
            serializer.writeStringList($4, nonSparseList, $4.listMember());
        }
        if (nonSparseMap != null) {
            serializer.writeStringMap($5, nonSparseMap, $5.mapKeyMember(), $5.mapValueMember());
        }
        if (nonSparseListOfSparseMap != null) {
            serializer.writeList($6, nonSparseListOfSparseMap, nonSparseListOfSparseMap.size(), SharedSerde.NonSparseListOfSparseMapSerializer.INSTANCE);
        }
        if (sparseMapOfNonSparseList != null) {
            serializer.writeMap($7, sparseMapOfNonSparseList, sparseMapOfNonSparseList.size(), SharedSerde.SparseMapOfNonSparseListSerializer.INSTANCE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) sparseList;
            case 1 -> (T) sparseMap;
            case 2 -> (T) sparseListOfSparseMap;
            case 3 -> (T) nonSparseListOfNonSparseMap;
            case 4 -> (T) nonSparseList;
            case 5 -> (T) nonSparseMap;
            case 6 -> (T) nonSparseListOfSparseMap;
            case 7 -> (T) sparseMapOfNonSparseList;
            default -> throw SchemaUtils.unknownMember(member);
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link CollectionStruct}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.nonSparseList(this.nonSparseList);
        builder.sparseList(this.sparseList);
        builder.nonSparseMap(this.nonSparseMap);
        builder.sparseMap(this.sparseMap);
        builder.nonSparseListOfSparseMap(this.nonSparseListOfSparseMap);
        builder.sparseListOfSparseMap(this.sparseListOfSparseMap);
        builder.sparseMapOfNonSparseList(this.sparseMapOfNonSparseList);
        builder.nonSparseListOfNonSparseMap(this.nonSparseListOfNonSparseMap);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CollectionStruct}.
     */
    public static final class Builder implements ShapeBuilder<CollectionStruct> {
        private long $setMembers;
        private List<String> nonSparseList;
        private List<@Nullable String> sparseList;
        private Map<String, String> nonSparseMap;
        private Map<String, @Nullable String> sparseMap;
        private List<Map<String, @Nullable String>> nonSparseListOfSparseMap;
        private List<@Nullable Map<String, @Nullable String>> sparseListOfSparseMap;
        private Map<String, @Nullable List<String>> sparseMapOfNonSparseList;
        private List<Map<String, String>> nonSparseListOfNonSparseMap;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * Non-sparse list: List&lt;String&gt;
         *
         * @return this builder.
         */
        public Builder nonSparseList(@Nullable List<String> nonSparseList) {
            this.nonSparseList = nonSparseList;
            return this;
        }

        /**
         * Sparse list: List&lt;{@literal @}Nullable String&gt;
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseList(List<@Nullable String> sparseList) {
            this.sparseList = SchemaUtils.requireNonNull(sparseList, $0);
            $setMembers |= 0x1L;
            return this;
        }

        /**
         * Non-sparse map: Map&lt;String, String&gt;
         *
         * @return this builder.
         */
        public Builder nonSparseMap(@Nullable Map<String, String> nonSparseMap) {
            this.nonSparseMap = nonSparseMap;
            return this;
        }

        /**
         * Sparse map: Map&lt;String, {@literal @}Nullable String&gt;
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseMap(Map<String, @Nullable String> sparseMap) {
            this.sparseMap = SchemaUtils.requireNonNull(sparseMap, $1);
            $setMembers |= 0x2L;
            return this;
        }

        /**
         * Non-sparse list of sparse map: List&lt;Map&lt;String, {@literal @}Nullable String&gt;&gt;
         *
         * @return this builder.
         */
        public Builder nonSparseListOfSparseMap(@Nullable List<Map<String, @Nullable String>> nonSparseListOfSparseMap) {
            this.nonSparseListOfSparseMap = nonSparseListOfSparseMap;
            return this;
        }

        /**
         * Sparse list of sparse map: List&lt;{@literal @}Nullable Map&lt;String, {@literal @}Nullable String&gt;&gt;
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseListOfSparseMap(List<@Nullable Map<String, @Nullable String>> sparseListOfSparseMap) {
            this.sparseListOfSparseMap = SchemaUtils.requireNonNull(sparseListOfSparseMap, $2);
            $setMembers |= 0x4L;
            return this;
        }

        /**
         * Sparse map of non-sparse list: Map&lt;String, {@literal @}Nullable List&lt;String&gt;&gt;
         *
         * @return this builder.
         */
        public Builder sparseMapOfNonSparseList(@Nullable Map<String, @Nullable List<String>> sparseMapOfNonSparseList) {
            this.sparseMapOfNonSparseList = sparseMapOfNonSparseList;
            return this;
        }

        /**
         * Non-sparse list of non-sparse map: List&lt;Map&lt;String, String&gt;&gt;
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder nonSparseListOfNonSparseMap(List<Map<String, String>> nonSparseListOfNonSparseMap) {
            this.nonSparseListOfNonSparseMap = SchemaUtils.requireNonNull(nonSparseListOfNonSparseMap, $3);
            $setMembers |= 0x8L;
            return this;
        }

        @Override
        public CollectionStruct build() {
            if ($setMembers != 0xfL) {
                PresenceTracker.validateRequiredMembers($SCHEMA, $setMembers);
            }
            return new CollectionStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> sparseList((List<@Nullable String>) value);
                case 1 -> sparseMap((Map<String, @Nullable String>) value);
                case 2 -> sparseListOfSparseMap((List<@Nullable Map<String, @Nullable String>>) value);
                case 3 -> nonSparseListOfNonSparseMap((List<Map<String, String>>) value);
                case 4 -> nonSparseList((List<String>) value);
                case 5 -> nonSparseMap((Map<String, String>) value);
                case 6 -> nonSparseListOfSparseMap((List<Map<String, @Nullable String>>) value);
                case 7 -> sparseMapOfNonSparseList((Map<String, @Nullable List<String>>) value);
                default -> throw SchemaUtils.unknownMember(member);
            }
        }

        @Override
        public ShapeBuilder<CollectionStruct> errorCorrection() {
            if ($setMembers == 0xfL) {
                return this;
            }
            if (($setMembers & 0x1L) == 0L) {
                sparseList(Collections.emptyList());
            }
            if (($setMembers & 0x2L) == 0L) {
                sparseMap(Collections.emptyMap());
            }
            if (($setMembers & 0x4L) == 0L) {
                sparseListOfSparseMap(Collections.emptyList());
            }
            if (($setMembers & 0x8L) == 0L) {
                nonSparseListOfNonSparseMap(Collections.emptyList());
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
                case 0 -> builder.sparseList(SharedSerde.deserializeSparseStringList(member, de));
                case 1 -> builder.sparseMap(SharedSerde.deserializeSparseStringMap(member, de));
                case 2 -> builder.sparseListOfSparseMap(SharedSerde.deserializeSparseListOfSparseMap(member, de));
                case 3 -> builder.nonSparseListOfNonSparseMap(SharedSerde.deserializeNonSparseListOfNonSparseMap(member, de));
                case 4 -> builder.nonSparseList(SharedSerde.deserializeNonSparseStringList(member, de));
                case 5 -> builder.nonSparseMap(SharedSerde.deserializeNonSparseStringMap(member, de));
                case 6 -> builder.nonSparseListOfSparseMap(SharedSerde.deserializeNonSparseListOfSparseMap(member, de));
                case 7 -> builder.sparseMapOfNonSparseList(SharedSerde.deserializeSparseMapOfNonSparseList(member, de));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
