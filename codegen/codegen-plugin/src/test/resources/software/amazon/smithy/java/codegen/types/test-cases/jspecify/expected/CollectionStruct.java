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
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Structure exercising all collection sparsity combinations
 */
@SmithyGenerated
@NullMarked
public final class CollectionStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.COLLECTION_STRUCT;
    private static final Schema $SCHEMA_NON_SPARSE_LIST = $SCHEMA.member("nonSparseList");
    private static final Schema $SCHEMA_SPARSE_LIST = $SCHEMA.member("sparseList");
    private static final Schema $SCHEMA_NON_SPARSE_MAP = $SCHEMA.member("nonSparseMap");
    private static final Schema $SCHEMA_SPARSE_MAP = $SCHEMA.member("sparseMap");
    private static final Schema $SCHEMA_NON_SPARSE_LIST_OF_SPARSE_MAP = $SCHEMA.member("nonSparseListOfSparseMap");
    private static final Schema $SCHEMA_SPARSE_LIST_OF_SPARSE_MAP = $SCHEMA.member("sparseListOfSparseMap");
    private static final Schema $SCHEMA_SPARSE_MAP_OF_NON_SPARSE_LIST = $SCHEMA.member("sparseMapOfNonSparseList");
    private static final Schema $SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP = $SCHEMA.member("nonSparseListOfNonSparseMap");

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
     * Non-sparse list: List<String>
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
     * Sparse list: List<@Nullable String>
     */
    public List<@Nullable String> getSparseList() {
        return sparseList;
    }

    public boolean hasSparseList() {
        return true;
    }

    /**
     * Non-sparse map: Map<String, String>
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
     * Sparse map: Map<String, @Nullable String>
     */
    public Map<String, @Nullable String> getSparseMap() {
        return sparseMap;
    }

    public boolean hasSparseMap() {
        return true;
    }

    /**
     * Non-sparse list of sparse map: List<Map<String, @Nullable String>>
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
     * Sparse list of sparse map: List<@Nullable Map<String, @Nullable String>>
     */
    public List<@Nullable Map<String, @Nullable String>> getSparseListOfSparseMap() {
        return sparseListOfSparseMap;
    }

    public boolean hasSparseListOfSparseMap() {
        return true;
    }

    /**
     * Sparse map of non-sparse list: Map<String, @Nullable List<String>>
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
     * Non-sparse list of non-sparse map: List<Map<String, String>>
     */
    public List<Map<String, String>> getNonSparseListOfNonSparseMap() {
        return nonSparseListOfNonSparseMap;
    }

    public boolean hasNonSparseListOfNonSparseMap() {
        return true;
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
        CollectionStruct that = (CollectionStruct) other;
        return Objects.equals(this.nonSparseList, that.nonSparseList)
               && Objects.equals(this.sparseList, that.sparseList)
               && Objects.equals(this.nonSparseMap, that.nonSparseMap)
               && Objects.equals(this.sparseMap, that.sparseMap)
               && Objects.equals(this.nonSparseListOfSparseMap, that.nonSparseListOfSparseMap)
               && Objects.equals(this.sparseListOfSparseMap, that.sparseListOfSparseMap)
               && Objects.equals(this.sparseMapOfNonSparseList, that.sparseMapOfNonSparseList)
               && Objects.equals(this.nonSparseListOfNonSparseMap, that.nonSparseListOfNonSparseMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nonSparseList, sparseList, nonSparseMap, sparseMap, nonSparseListOfSparseMap, sparseListOfSparseMap, sparseMapOfNonSparseList, nonSparseListOfNonSparseMap);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (nonSparseList != null) {
            serializer.writeList($SCHEMA_NON_SPARSE_LIST, nonSparseList, nonSparseList.size(), SharedSerde.NonSparseStringListSerializer.INSTANCE);
        }
        serializer.writeList($SCHEMA_SPARSE_LIST, sparseList, sparseList.size(), SharedSerde.SparseStringListSerializer.INSTANCE);
        if (nonSparseMap != null) {
            serializer.writeMap($SCHEMA_NON_SPARSE_MAP, nonSparseMap, nonSparseMap.size(), SharedSerde.NonSparseStringMapSerializer.INSTANCE);
        }
        serializer.writeMap($SCHEMA_SPARSE_MAP, sparseMap, sparseMap.size(), SharedSerde.SparseStringMapSerializer.INSTANCE);
        if (nonSparseListOfSparseMap != null) {
            serializer.writeList($SCHEMA_NON_SPARSE_LIST_OF_SPARSE_MAP, nonSparseListOfSparseMap, nonSparseListOfSparseMap.size(), SharedSerde.NonSparseListOfSparseMapSerializer.INSTANCE);
        }
        serializer.writeList($SCHEMA_SPARSE_LIST_OF_SPARSE_MAP, sparseListOfSparseMap, sparseListOfSparseMap.size(), SharedSerde.SparseListOfSparseMapSerializer.INSTANCE);
        if (sparseMapOfNonSparseList != null) {
            serializer.writeMap($SCHEMA_SPARSE_MAP_OF_NON_SPARSE_LIST, sparseMapOfNonSparseList, sparseMapOfNonSparseList.size(), SharedSerde.SparseMapOfNonSparseListSerializer.INSTANCE);
        }
        serializer.writeList($SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP, nonSparseListOfNonSparseMap, nonSparseListOfNonSparseMap.size(), SharedSerde.NonSparseListOfNonSparseMapSerializer.INSTANCE);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, sparseList);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP, member, sparseMap);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST_OF_SPARSE_MAP, member, sparseListOfSparseMap);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP, member, nonSparseListOfNonSparseMap);
            case 4 -> (T) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST, member, nonSparseList);
            case 5 -> (T) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_MAP, member, nonSparseMap);
            case 6 -> (T) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST_OF_SPARSE_MAP, member, nonSparseListOfSparseMap);
            case 7 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP_OF_NON_SPARSE_LIST, member, sparseMapOfNonSparseList);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
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
        private final PresenceTracker tracker = PresenceTracker.of($SCHEMA);
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
         * Non-sparse list: List<String>
         *
         * @return this builder.
         */
        public Builder nonSparseList(@Nullable List<String> nonSparseList) {
            this.nonSparseList = nonSparseList;
            return this;
        }

        /**
         * Sparse list: List<@Nullable String>
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseList(List<@Nullable String> sparseList) {
            this.sparseList = Objects.requireNonNull(sparseList, "sparseList cannot be null");
            tracker.setMember($SCHEMA_SPARSE_LIST);
            return this;
        }

        /**
         * Non-sparse map: Map<String, String>
         *
         * @return this builder.
         */
        public Builder nonSparseMap(@Nullable Map<String, String> nonSparseMap) {
            this.nonSparseMap = nonSparseMap;
            return this;
        }

        /**
         * Sparse map: Map<String, @Nullable String>
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseMap(Map<String, @Nullable String> sparseMap) {
            this.sparseMap = Objects.requireNonNull(sparseMap, "sparseMap cannot be null");
            tracker.setMember($SCHEMA_SPARSE_MAP);
            return this;
        }

        /**
         * Non-sparse list of sparse map: List<Map<String, @Nullable String>>
         *
         * @return this builder.
         */
        public Builder nonSparseListOfSparseMap(@Nullable List<Map<String, @Nullable String>> nonSparseListOfSparseMap) {
            this.nonSparseListOfSparseMap = nonSparseListOfSparseMap;
            return this;
        }

        /**
         * Sparse list of sparse map: List<@Nullable Map<String, @Nullable String>>
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder sparseListOfSparseMap(List<@Nullable Map<String, @Nullable String>> sparseListOfSparseMap) {
            this.sparseListOfSparseMap = Objects.requireNonNull(sparseListOfSparseMap, "sparseListOfSparseMap cannot be null");
            tracker.setMember($SCHEMA_SPARSE_LIST_OF_SPARSE_MAP);
            return this;
        }

        /**
         * Sparse map of non-sparse list: Map<String, @Nullable List<String>>
         *
         * @return this builder.
         */
        public Builder sparseMapOfNonSparseList(@Nullable Map<String, @Nullable List<String>> sparseMapOfNonSparseList) {
            this.sparseMapOfNonSparseList = sparseMapOfNonSparseList;
            return this;
        }

        /**
         * Non-sparse list of non-sparse map: List<Map<String, String>>
         *
         * <p><strong>Required</strong>
         * @return this builder.
         */
        public Builder nonSparseListOfNonSparseMap(List<Map<String, String>> nonSparseListOfNonSparseMap) {
            this.nonSparseListOfNonSparseMap = Objects.requireNonNull(nonSparseListOfNonSparseMap, "nonSparseListOfNonSparseMap cannot be null");
            tracker.setMember($SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP);
            return this;
        }

        @Override
        public CollectionStruct build() {
            tracker.validate();
            return new CollectionStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> sparseList((List<@Nullable String>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, value));
                case 1 -> sparseMap((Map<String, @Nullable String>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP, member, value));
                case 2 -> sparseListOfSparseMap((List<@Nullable Map<String, @Nullable String>>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST_OF_SPARSE_MAP, member, value));
                case 3 -> nonSparseListOfNonSparseMap((List<Map<String, String>>) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP, member, value));
                case 4 -> nonSparseList((List<String>) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST, member, value));
                case 5 -> nonSparseMap((Map<String, String>) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_MAP, member, value));
                case 6 -> nonSparseListOfSparseMap((List<Map<String, @Nullable String>>) SchemaUtils.validateSameMember($SCHEMA_NON_SPARSE_LIST_OF_SPARSE_MAP, member, value));
                case 7 -> sparseMapOfNonSparseList((Map<String, @Nullable List<String>>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP_OF_NON_SPARSE_LIST, member, value));
                default -> ShapeBuilder.super.setMemberValue(member, value);
            }
        }

        @Override
        public ShapeBuilder<CollectionStruct> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember($SCHEMA_SPARSE_LIST)) {
                sparseList(Collections.emptyList());
            }
            if (!tracker.checkMember($SCHEMA_SPARSE_MAP)) {
                sparseMap(Collections.emptyMap());
            }
            if (!tracker.checkMember($SCHEMA_SPARSE_LIST_OF_SPARSE_MAP)) {
                sparseListOfSparseMap(Collections.emptyList());
            }
            if (!tracker.checkMember($SCHEMA_NON_SPARSE_LIST_OF_NON_SPARSE_MAP)) {
                nonSparseListOfNonSparseMap(Collections.emptyList());
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
                    case 0 -> builder.sparseList(SharedSerde.deserializeSparseStringList(member, de));
                    case 1 -> builder.sparseMap(SharedSerde.deserializeSparseStringMap(member, de));
                    case 2 -> builder.sparseListOfSparseMap(SharedSerde.deserializeSparseListOfSparseMap(member, de));
                    case 3 -> builder.nonSparseListOfNonSparseMap(SharedSerde.deserializeNonSparseListOfNonSparseMap(member, de));
                    case 4 -> builder.nonSparseList(SharedSerde.deserializeNonSparseStringList(member, de));
                    case 5 -> builder.nonSparseMap(SharedSerde.deserializeNonSparseStringMap(member, de));
                    case 6 -> builder.nonSparseListOfSparseMap(SharedSerde.deserializeNonSparseListOfSparseMap(member, de));
                    case 7 -> builder.sparseMapOfNonSparseList(SharedSerde.deserializeSparseMapOfNonSparseList(member, de));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
