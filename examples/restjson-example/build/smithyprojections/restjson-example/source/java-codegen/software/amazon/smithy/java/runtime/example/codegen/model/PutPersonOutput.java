/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.SequencedSet;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonOutput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema.memberBuilder("favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpHeaderTrait("X-Favorite-Color")
        )
        .build();

    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder("age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new JsonNameTrait("Age"),
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder("birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_STATUS = SdkSchema.memberBuilder("status", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new HttpResponseCodeTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_LIST = SdkSchema.memberBuilder("list", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_SET = SdkSchema.memberBuilder("set", SharedSchemas.SET_OF_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_LIST = SdkSchema.memberBuilder("requiredList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME,
            SCHEMA_FAVORITE_COLOR,
            SCHEMA_AGE,
            SCHEMA_BIRTHDAY,
            SCHEMA_STATUS,
            SCHEMA_LIST,
            SCHEMA_SET,
            SCHEMA_REQUIRED_LIST
        )
        .build();

    private final String name;
    private final String favoriteColor;
    private final int age;
    private final Instant birthday;
    private final Integer status;
    private final List<String> list;
    private final SequencedSet<String> set;
    private final List<String> requiredList;

    private PutPersonOutput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.favoriteColor = builder.favoriteColor;
        this.age = SmithyBuilder.requiredState("age", builder.age);
        this.birthday = builder.birthday;
        this.status = builder.status;
        this.list = builder.list != null ? Collections.unmodifiableList(builder.list) : null;
        this.set = builder.set != null ? Collections.unmodifiableSequencedSet(builder.set) : null;
        this.requiredList = Collections.unmodifiableList(builder.requiredList);
    }

    public String name() {
        return name;
    }

    public String favoriteColor() {
        return favoriteColor;
    }

    public Integer age() {
        return age;
    }

    public Instant birthday() {
        return birthday;
    }

    public int status() {
        return status;
    }

    public List<String> list() {
        return list != null ? list : Collections.emptyList();
    }

    public boolean hasList() {
        return list != null;
    }

    public SequencedSet<String> set() {
        return set != null ? set : Collections.emptySortedSet();
    }

    public boolean hasSet() {
        return set != null;
    }

    public List<String> requiredList() {
        return requiredList;
    }

    public boolean hasRequiredList() {
        return requiredList != null;
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
        PutPersonOutput that = (PutPersonOutput) other;
        return Objects.equals(name, that.name)
               && Objects.equals(favoriteColor, that.favoriteColor)
               && age == that.age
               && Objects.equals(birthday, that.birthday)
               && Objects.equals(status, that.status)
               && Objects.equals(list, that.list)
               && Objects.equals(set, that.set)
               && Objects.equals(requiredList, that.requiredList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, favoriteColor, age, birthday, status, list, set, requiredList);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            st.writeString(SCHEMA_NAME, name);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_FAVORITE_COLOR, favoriteColor);
            st.writeInteger(SCHEMA_AGE, age);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_BIRTHDAY, birthday);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_STATUS, status);
            if (list != null) {
                var SCHEMA_LIST_MEMBER = SCHEMA_LIST.member("member");
                st.writeList(SCHEMA_LIST, stl -> list.forEach(listElem -> {
                    stl.writeString(SCHEMA_LIST_MEMBER, listElem);
                }));
            };
            if (set != null) {
                var SCHEMA_SET_MEMBER = SCHEMA_SET.member("member");
                st.writeList(SCHEMA_SET, stl -> set.forEach(setElem -> {
                    stl.writeString(SCHEMA_SET_MEMBER, setElem);
                }));
            };
            var SCHEMA_REQUIRED_LIST_MEMBER = SCHEMA_REQUIRED_LIST.member("member");
            st.writeList(SCHEMA_REQUIRED_LIST, stl -> requiredList.forEach(requiredListElem -> {
                stl.writeString(SCHEMA_REQUIRED_LIST_MEMBER, requiredListElem);
            }));
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonOutput}.
     */
    public static final class Builder implements SdkShapeBuilder<PutPersonOutput> {
        private String name;
        private String favoriteColor;
        private int age = 1;
        private Instant birthday;
        private Integer status;
        private List<String> list;
        private SequencedSet<String> set;
        private List<String> requiredList;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder favoriteColor(String favoriteColor) {
            this.favoriteColor = favoriteColor;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder birthday(Instant birthday) {
            this.birthday = birthday;
            return this;
        }

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        public Builder list(Collection<String> list) {
            this.list = list != null ? new ArrayList<>(list) : null;
            return this;
        }

        public Builder addAllList(Collection<String> list) {
            if (this.list == null) {
                this.list = new ArrayList<>(list);
            } else {
                this.list.addAll(list);
            }
            return this;
        }

        public Builder list(String list) {
            if (this.list == null) {
                this.list = new ArrayList<>();
            }
            this.list.add(list);
            return this;
        }

        public Builder list(String... list) {
            if (this.list == null) {
                this.list = new ArrayList<>();
            }
            Collections.addAll(this.list, list);
            return this;
        }

        public Builder set(Collection<String> set) {
            this.set = set != null ? new LinkedHashSet<>(set) : null;
            return this;
        }

        public Builder addAllSet(Collection<String> set) {
            if (this.set == null) {
                this.set = new LinkedHashSet<>(set);
            } else {
                this.set.addAll(set);
            }
            return this;
        }

        public Builder set(String set) {
            if (this.set == null) {
                this.set = new LinkedHashSet<>();
            }
            this.set.add(set);
            return this;
        }

        public Builder set(String... set) {
            if (this.set == null) {
                this.set = new LinkedHashSet<>();
            }
            Collections.addAll(this.set, set);
            return this;
        }

        public Builder requiredList(Collection<String> requiredList) {
            this.requiredList = requiredList != null ? new ArrayList<>(requiredList) : null;
            return this;
        }

        public Builder addAllRequiredList(Collection<String> requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>(requiredList);
            } else {
                this.requiredList.addAll(requiredList);
            }
            return this;
        }

        public Builder requiredList(String requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
            }
            this.requiredList.add(requiredList);
            return this;
        }

        public Builder requiredList(String... requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
            }
            Collections.addAll(this.requiredList, requiredList);
            return this;
        }

        @Override
        public PutPersonOutput build() {
            return new PutPersonOutput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

