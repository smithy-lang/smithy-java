package software.amazon.smithy.java.example.standalone.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
public final class CollectionsStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.COLLECTIONS_STRUCT;
    private static final Schema $SCHEMA_DENSE_LIST = $SCHEMA.member("denseList");
    private static final Schema $SCHEMA_SPARSE_LIST = $SCHEMA.member("sparseList");
    private static final Schema $SCHEMA_DENSE_MAP = $SCHEMA.member("denseMap");
    private static final Schema $SCHEMA_SPARSE_MAP = $SCHEMA.member("sparseMap");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient List<String> denseList;
    private final transient List<String> sparseList;
    private final transient Map<String, String> denseMap;
    private final transient Map<String, String> sparseMap;

    private CollectionsStruct(Builder builder) {
        this.denseList = builder.denseList == null ? null : Collections.unmodifiableList(builder.denseList);
        this.sparseList = builder.sparseList == null ? null : Collections.unmodifiableList(builder.sparseList);
        this.denseMap = builder.denseMap == null ? null : Collections.unmodifiableMap(builder.denseMap);
        this.sparseMap = builder.sparseMap == null ? null : Collections.unmodifiableMap(builder.sparseMap);
    }

    public List<String> getDenseList() {
        if (denseList == null) {
            return Collections.emptyList();
        }
        return denseList;
    }

    public boolean hasDenseList() {
        return denseList != null;
    }

    public List<String> getSparseList() {
        if (sparseList == null) {
            return Collections.emptyList();
        }
        return sparseList;
    }

    public boolean hasSparseList() {
        return sparseList != null;
    }

    public Map<String, String> getDenseMap() {
        if (denseMap == null) {
            return Collections.emptyMap();
        }
        return denseMap;
    }

    public boolean hasDenseMap() {
        return denseMap != null;
    }

    public Map<String, String> getSparseMap() {
        if (sparseMap == null) {
            return Collections.emptyMap();
        }
        return sparseMap;
    }

    public boolean hasSparseMap() {
        return sparseMap != null;
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
        CollectionsStruct that = (CollectionsStruct) other;
        return Objects.equals(this.denseList, that.denseList)
               && Objects.equals(this.sparseList, that.sparseList)
               && Objects.equals(this.denseMap, that.denseMap)
               && Objects.equals(this.sparseMap, that.sparseMap);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(denseList);
        $hc = 31 * $hc + Objects.hashCode(sparseList);
        $hc = 31 * $hc + Objects.hashCode(denseMap);
        $hc = 31 * $hc + Objects.hashCode(sparseMap);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (denseList != null) {
            serializer.writeList($SCHEMA_DENSE_LIST, denseList, denseList.size(), SharedSerde.DenseListSerializer.INSTANCE);
        }
        if (sparseList != null) {
            serializer.writeList($SCHEMA_SPARSE_LIST, sparseList, sparseList.size(), SharedSerde.SparseListSerializer.INSTANCE);
        }
        if (denseMap != null) {
            serializer.writeMap($SCHEMA_DENSE_MAP, denseMap, denseMap.size(), SharedSerde.DenseMapSerializer.INSTANCE);
        }
        if (sparseMap != null) {
            serializer.writeMap($SCHEMA_SPARSE_MAP, sparseMap, sparseMap.size(), SharedSerde.SparseMapSerializer.INSTANCE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_DENSE_LIST, member, denseList);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, sparseList);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_DENSE_MAP, member, denseMap);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP, member, sparseMap);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link CollectionsStruct}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.denseList(this.denseList);
        builder.sparseList(this.sparseList);
        builder.denseMap(this.denseMap);
        builder.sparseMap(this.sparseMap);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CollectionsStruct}.
     */
    public static final class Builder implements ShapeBuilder<CollectionsStruct> {
        private List<String> denseList;
        private List<String> sparseList;
        private Map<String, String> denseMap;
        private Map<String, String> sparseMap;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder denseList(List<String> denseList) {
            this.denseList = denseList;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder sparseList(List<String> sparseList) {
            this.sparseList = sparseList;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder denseMap(Map<String, String> denseMap) {
            this.denseMap = denseMap;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder sparseMap(Map<String, String> sparseMap) {
            this.sparseMap = sparseMap;
            return this;
        }

        @Override
        public CollectionsStruct build() {
            return new CollectionsStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> denseList((List<String>) SchemaUtils.validateSameMember($SCHEMA_DENSE_LIST, member, value));
                case 1 -> sparseList((List<String>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_LIST, member, value));
                case 2 -> denseMap((Map<String, String>) SchemaUtils.validateSameMember($SCHEMA_DENSE_MAP, member, value));
                case 3 -> sparseMap((Map<String, String>) SchemaUtils.validateSameMember($SCHEMA_SPARSE_MAP, member, value));
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
                    case 0 -> builder.denseList(SharedSerde.deserializeDenseList(member, de));
                    case 1 -> builder.sparseList(SharedSerde.deserializeSparseList(member, de));
                    case 2 -> builder.denseMap(SharedSerde.deserializeDenseMap(member, de));
                    case 3 -> builder.sparseMap(SharedSerde.deserializeSparseMap(member, de));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}
