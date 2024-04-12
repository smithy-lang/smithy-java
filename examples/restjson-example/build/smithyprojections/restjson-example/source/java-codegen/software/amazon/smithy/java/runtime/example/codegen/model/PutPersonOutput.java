/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.OutputTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.SmithyBuilder;


public final class PutPersonOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonOutput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()

        )
        .build();

    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema.memberBuilder(1, "favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpHeaderTrait("X-Favorite-Color")
        )
        .build();

    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder(2, "age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new JsonNameTrait("Age")
        )
        .build();

    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder(3, "birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_STATUS = SdkSchema.memberBuilder(4, "status", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new HttpResponseCodeTrait()

        )
        .build();

    private static final SdkSchema SCHEMA_LIST = SdkSchema.memberBuilder(5, "list", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(
            new OutputTrait()

        )
        .members(
            SCHEMA_NAME,
            SCHEMA_FAVORITE_COLOR,
            SCHEMA_AGE,
            SCHEMA_BIRTHDAY,
            SCHEMA_STATUS,
            SCHEMA_LIST
        )
        .build();

    private final String name;
    private final String favoriteColor;
    private final Integer age;
    private final Instant birthday;
    private final Integer status;
    private final List<String> list;

    private PutPersonOutput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.favoriteColor = builder.favoriteColor;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.status = builder.status;
        this.list = builder.list.hasValue() ? builder.list.copy() : null;
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

    public Integer status() {
        return status;
    }

    public List<String> list() {
        return list != null ? list : Collections.emptyList();
    }

    public boolean hasList() {
        return list != null;
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
               && Objects.equals(age, that.age)
               && Objects.equals(birthday, that.birthday)
               && Objects.equals(status, that.status)
               && Objects.equals(list, that.list);

    }

    @Override
    public int hashCode() {
        return Objects.hash(name, favoriteColor, age, birthday, status, list);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
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
        private Integer age;
        private Instant birthday;
        private Integer status;
        private final BuilderRef<List<String>> list = BuilderRef.forList();

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

        public Builder list(List<String> list) {
            clearList();
            this.list.get().addAll(list);
            return this;
        }

        public Builder clearList() {
            list.get().clear();
            return this;
        }

        public Builder addList(String value) {
            list.get().add(value);
            return this;
        }

        public Builder removeList(String value) {
            list.get().remove(value);
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

