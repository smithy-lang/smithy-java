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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class CollectionsStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.COLLECTIONS_STRUCT;
    private static final Schema $0 = $SCHEMA.member(0); // denseList
    private static final Schema $1 = $SCHEMA.member(1); // sparseList
    private static final Schema $2 = $SCHEMA.member(2); // denseMap
    private static final Schema $3 = $SCHEMA.member(3); // sparseMap

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
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof CollectionsStruct that)) {
            return false;
        }
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
            serializer.writeStringList($0, denseList, $0.listMember());
        }
        if (sparseList != null) {
            serializer.writeSparseStringList($1, sparseList, $1.listMember());
        }
        if (denseMap != null) {
            serializer.writeStringMap($2, denseMap, $2.mapKeyMember(), $2.mapValueMember());
        }
        if (sparseMap != null) {
            serializer.writeSparseStringMap($3, sparseMap, $3.mapKeyMember(), $3.mapValueMember());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) denseList;
            case 1 -> (T) sparseList;
            case 2 -> (T) denseMap;
            case 3 -> (T) sparseMap;
            default -> throw SchemaUtils.unknownMember(member);
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
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> denseList((List<String>) value);
                case 1 -> sparseList((List<String>) value);
                case 2 -> denseMap((Map<String, String>) value);
                case 3 -> sparseMap((Map<String, String>) value);
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
                case 0 -> builder.denseList(SharedSerde.deserializeDenseList(member, de));
                case 1 -> builder.sparseList(SharedSerde.deserializeSparseList(member, de));
                case 2 -> builder.denseMap(SharedSerde.deserializeDenseMap(member, de));
                case 3 -> builder.sparseMap(SharedSerde.deserializeSparseMap(member, de));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
