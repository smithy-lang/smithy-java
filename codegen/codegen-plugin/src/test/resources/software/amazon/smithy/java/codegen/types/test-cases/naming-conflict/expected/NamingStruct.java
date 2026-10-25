package software.amazon.smithy.java.example.standalone.model;

import java.util.Collections;
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
public final class NamingStruct implements SerializableStruct {

    public static final Schema $SCHEMA = Schemas.NAMING_STRUCT;
    private static final Schema $0 = $SCHEMA.member(0); // other
    private static final Schema $1 = $SCHEMA.member(1); // builder
    private static final Schema $2 = $SCHEMA.member(2); // type
    private static final Schema $3 = $SCHEMA.member(3); // object
    private static final Schema $4 = $SCHEMA.member(4); // union
    private static final Schema $5 = $SCHEMA.member(5); // map
    private static final Schema $6 = $SCHEMA.member(6); // list
    private static final Schema $7 = $SCHEMA.member(7); // listOfList
    private static final Schema $8 = $SCHEMA.member(8); // mapOfMap

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
        return SchemaUtils.toString(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof NamingStruct that)) {
            return false;
        }
        return Objects.equals(this.other, that.other)
               && Objects.equals(this.union, that.union)
               && Objects.equals(this.builderMember, that.builderMember)
               && Objects.equals(this.type, that.type)
               && Objects.equals(this.objectMember, that.objectMember)
               && Objects.equals(this.map, that.map)
               && Objects.equals(this.list, that.list)
               && Objects.equals(this.listOfList, that.listOfList)
               && Objects.equals(this.mapOfMap, that.mapOfMap);
    }

    @Override
    public int hashCode() {
        int $hc = Objects.hashCode(other);
        $hc = 31 * $hc + Objects.hashCode(builderMember);
        $hc = 31 * $hc + Objects.hashCode(type);
        $hc = 31 * $hc + Objects.hashCode(objectMember);
        $hc = 31 * $hc + Objects.hashCode(union);
        $hc = 31 * $hc + Objects.hashCode(map);
        $hc = 31 * $hc + Objects.hashCode(list);
        $hc = 31 * $hc + Objects.hashCode(listOfList);
        $hc = 31 * $hc + Objects.hashCode(mapOfMap);
        return $hc;
    }

    @Override
    public Schema schema() {
        return $SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (other != null) {
            serializer.writeString($0, other);
        }
        if (builderMember != null) {
            serializer.writeStruct($1, builderMember);
        }
        if (type != null) {
            serializer.writeStruct($2, type);
        }
        if (objectMember != null) {
            serializer.writeStruct($3, objectMember);
        }
        if (union != null) {
            serializer.writeStruct($4, union);
        }
        if (map != null) {
            serializer.writeStruct($5, map);
        }
        if (list != null) {
            serializer.writeStruct($6, list);
        }
        if (listOfList != null) {
            serializer.writeStructList($7, listOfList, $7.listMember());
        }
        if (mapOfMap != null) {
            serializer.writeStructMap($8, mapOfMap, $8.mapKeyMember(), $8.mapValueMember());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMemberValue(Schema member) {
        SchemaUtils.validateMemberOf($SCHEMA, member);
        return switch (member.memberIndex()) {
            case 0 -> (T) other;
            case 1 -> (T) builderMember;
            case 2 -> (T) type;
            case 3 -> (T) objectMember;
            case 4 -> (T) union;
            case 5 -> (T) map;
            case 6 -> (T) list;
            case 7 -> (T) listOfList;
            case 8 -> (T) mapOfMap;
            default -> throw SchemaUtils.unknownMember(member);
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
            SchemaUtils.validateMemberOf($SCHEMA, member);
            switch (member.memberIndex()) {
                case 0 -> other((String) value);
                case 1 -> builderMember((BuilderShape) value);
                case 2 -> type((Type) value);
                case 3 -> objectMember((ObjectShape) value);
                case 4 -> union((UnionWithTypeMember) value);
                case 5 -> map((Map) value);
                case 6 -> list((List) value);
                case 7 -> listOfList((java.util.List<List>) value);
                case 8 -> mapOfMap((java.util.Map<String, Map>) value);
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
                case 0 -> builder.other(de.readString(member));
                case 1 -> builder.builderMember(BuilderShape.builder().deserializeMember(de, member).build());
                case 2 -> builder.type(Type.builder().deserializeMember(de, member).build());
                case 3 -> builder.objectMember(ObjectShape.builder().deserializeMember(de, member).build());
                case 4 -> builder.union(UnionWithTypeMember.builder().deserializeMember(de, member).build());
                case 5 -> builder.map(Map.builder().deserializeMember(de, member).build());
                case 6 -> builder.list(List.builder().deserializeMember(de, member).build());
                case 7 -> builder.listOfList(SharedSerde.deserializeListOfList(member, de));
                case 8 -> builder.mapOfMap(SharedSerde.deserializeMapOfMap(member, de));
                default -> throw SchemaUtils.unknownMember(member);
            }
        }
    }
}
