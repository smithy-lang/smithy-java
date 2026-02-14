
package software.amazon.smithy.java.example.standalone.model;

import java.util.Collections;
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
public final class NamingStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.NAMING_STRUCT;
    private static final Schema $SCHEMA_OTHER = $SCHEMA.member("other");
    private static final Schema $SCHEMA_BUILDER_MEMBER = $SCHEMA.member("builder");
    private static final Schema $SCHEMA_TYPE = $SCHEMA.member("type");
    private static final Schema $SCHEMA_OBJECT_MEMBER = $SCHEMA.member("object");
    private static final Schema $SCHEMA_UNION = $SCHEMA.member("union");
    private static final Schema $SCHEMA_MAP = $SCHEMA.member("map");
    private static final Schema $SCHEMA_LIST = $SCHEMA.member("list");
    private static final Schema $SCHEMA_LIST_OF_LIST = $SCHEMA.member("listOfList");
    private static final Schema $SCHEMA_MAP_OF_MAP = $SCHEMA.member("mapOfMap");

    public static final ShapeId $ID = $SCHEMA.id();

    private final transient String other;
    private final transient BuilderShape builderMember;
    private final transient Type type;
    private final transient ObjectShape objectMember;
    private final transient UnionWithTypeMember union;
    private final transient Map map;
    private final transient List list;
    private final transient java.util.List<List> listOfList;
    private final transient java.util.Map<String, Map> mapOfMap;

    private NamingStruct(Builder builder) {
        this.other = builder.other;
        this.builderMember = builder.builderMember;
        this.type = builder.type;
        this.objectMember = builder.objectMember;
        this.union = builder.union;
        this.map = builder.map;
        this.list = builder.list;
        this.listOfList = builder.listOfList == null ? null : Collections.unmodifiableList(builder.listOfList);
        this.mapOfMap = builder.mapOfMap == null ? null : Collections.unmodifiableMap(builder.mapOfMap);
    }

    public String getOther() {
        return other;
    }

    public BuilderShape getBuilder() {
        return builderMember;
    }

    public Type getType() {
        return type;
    }

    public ObjectShape getObject() {
        return objectMember;
    }

    public UnionWithTypeMember getUnion() {
        return union;
    }

    public Map getMap() {
        return map;
    }

    public List getList() {
        return list;
    }

    public java.util.List<List> getListOfList() {
        if (listOfList == null) {
            return Collections.emptyList();
        }
        return listOfList;
    }

    public boolean hasListOfList() {
        return listOfList != null;
    }

    public java.util.Map<String, Map> getMapOfMap() {
        if (mapOfMap == null) {
            return Collections.emptyMap();
        }
        return mapOfMap;
    }

    public boolean hasMapOfMap() {
        return mapOfMap != null;
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
        NamingStruct that = (NamingStruct) other;
        return Objects.equals(this.other, that.other)
               && Objects.equals(this.builderMember, that.builderMember)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.objectMember, that.objectMember)
               && Objects.equals(this.union, that.union)
               && Objects.equals(this.map, that.map)
               && Objects.equals(this.list, that.list)
               && Objects.equals(this.listOfList, that.listOfList)
               && Objects.equals(this.mapOfMap, that.mapOfMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(other, builderMember, type, objectMember, union, map, list, listOfList, mapOfMap);
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (other != null) {
            serializer.writeString($SCHEMA_OTHER, other);
        }
        if (builderMember != null) {
            serializer.writeStruct($SCHEMA_BUILDER_MEMBER, builderMember);
        }
        if (type != null) {
            serializer.writeStruct($SCHEMA_TYPE, type);
        }
        if (objectMember != null) {
            serializer.writeStruct($SCHEMA_OBJECT_MEMBER, objectMember);
        }
        if (union != null) {
            serializer.writeStruct($SCHEMA_UNION, union);
        }
        if (map != null) {
            serializer.writeStruct($SCHEMA_MAP, map);
        }
        if (list != null) {
            serializer.writeStruct($SCHEMA_LIST, list);
        }
        if (listOfList != null) {
            serializer.writeList($SCHEMA_LIST_OF_LIST, listOfList, listOfList.size(), SharedSerde.ListOfListSerializer.INSTANCE);
        }
        if (mapOfMap != null) {
            serializer.writeMap($SCHEMA_MAP_OF_MAP, mapOfMap, mapOfMap.size(), SharedSerde.MapOfMapSerializer.INSTANCE);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        return switch (member.memberIndex()) {
            case 0 -> (T) SchemaUtils.validateSameMember($SCHEMA_OTHER, member, other);
            case 1 -> (T) SchemaUtils.validateSameMember($SCHEMA_BUILDER_MEMBER, member, builderMember);
            case 2 -> (T) SchemaUtils.validateSameMember($SCHEMA_TYPE, member, type);
            case 3 -> (T) SchemaUtils.validateSameMember($SCHEMA_OBJECT_MEMBER, member, objectMember);
            case 4 -> (T) SchemaUtils.validateSameMember($SCHEMA_UNION, member, union);
            case 5 -> (T) SchemaUtils.validateSameMember($SCHEMA_MAP, member, map);
            case 6 -> (T) SchemaUtils.validateSameMember($SCHEMA_LIST, member, list);
            case 7 -> (T) SchemaUtils.validateSameMember($SCHEMA_LIST_OF_LIST, member, listOfList);
            case 8 -> (T) SchemaUtils.validateSameMember($SCHEMA_MAP_OF_MAP, member, mapOfMap);
            default -> throw new IllegalArgumentException("Attempted to get non-existent member: " + member.id());
        };
    }

    /**
     * Create a new builder containing all the current property values of this object.
     *
     * <p><strong>Note:</strong> This method performs only a shallow copy of the original properties.
     *
     * @return a builder for {@link NamingStruct}.
     */
    public Builder toBuilder() {
        var builder = new Builder();
        builder.other(this.other);
        builder.builderMember(this.builderMember);
        builder.type(this.type);
        builder.objectMember(this.objectMember);
        builder.union(this.union);
        builder.map(this.map);
        builder.list(this.list);
        builder.listOfList(this.listOfList);
        builder.mapOfMap(this.mapOfMap);
        return builder;
    }

    /**
     * @return returns a new Builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NamingStruct}.
     */
    public static final class Builder implements ShapeBuilder<NamingStruct> {
        private String other;
        private BuilderShape builderMember;
        private Type type;
        private ObjectShape objectMember;
        private UnionWithTypeMember union;
        private Map map;
        private List list;
        private java.util.List<List> listOfList;
        private java.util.Map<String, Map> mapOfMap;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        /**
         * @return this builder.
         */
        public Builder other(String other) {
            this.other = other;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder builderMember(BuilderShape builderMember) {
            this.builderMember = builderMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder objectMember(ObjectShape objectMember) {
            this.objectMember = objectMember;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder union(UnionWithTypeMember union) {
            this.union = union;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder map(Map map) {
            this.map = map;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder list(List list) {
            this.list = list;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder listOfList(java.util.List<List> listOfList) {
            this.listOfList = listOfList;
            return this;
        }

        /**
         * @return this builder.
         */
        public Builder mapOfMap(java.util.Map<String, Map> mapOfMap) {
            this.mapOfMap = mapOfMap;
            return this;
        }

        @Override
        public NamingStruct build() {
            return new NamingStruct(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void setMemberValue(Schema member, Object value) {
            switch (member.memberIndex()) {
                case 0 -> other((String) SchemaUtils.validateSameMember($SCHEMA_OTHER, member, value));
                case 1 -> builderMember((BuilderShape) SchemaUtils.validateSameMember($SCHEMA_BUILDER_MEMBER, member, value));
                case 2 -> type((Type) SchemaUtils.validateSameMember($SCHEMA_TYPE, member, value));
                case 3 -> objectMember((ObjectShape) SchemaUtils.validateSameMember($SCHEMA_OBJECT_MEMBER, member, value));
                case 4 -> union((UnionWithTypeMember) SchemaUtils.validateSameMember($SCHEMA_UNION, member, value));
                case 5 -> map((Map) SchemaUtils.validateSameMember($SCHEMA_MAP, member, value));
                case 6 -> list((List) SchemaUtils.validateSameMember($SCHEMA_LIST, member, value));
                case 7 -> listOfList((java.util.List<List>) SchemaUtils.validateSameMember($SCHEMA_LIST_OF_LIST, member, value));
                case 8 -> mapOfMap((java.util.Map<String, Map>) SchemaUtils.validateSameMember($SCHEMA_MAP_OF_MAP, member, value));
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
                    case 0 -> builder.other(de.readString(member));
                    case 1 -> builder.builderMember(BuilderShape.builder().deserializeMember(de, member).build());
                    case 2 -> builder.type(Type.builder().deserializeMember(de, member).build());
                    case 3 -> builder.objectMember(ObjectShape.builder().deserializeMember(de, member).build());
                    case 4 -> builder.union(UnionWithTypeMember.builder().deserializeMember(de, member).build());
                    case 5 -> builder.map(Map.builder().deserializeMember(de, member).build());
                    case 6 -> builder.list(List.builder().deserializeMember(de, member).build());
                    case 7 -> builder.listOfList(SharedSerde.deserializeListOfList(member, de));
                    case 8 -> builder.mapOfMap(SharedSerde.deserializeMapOfMap(member, de));
                    default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                }
            }
        }
    }
}

