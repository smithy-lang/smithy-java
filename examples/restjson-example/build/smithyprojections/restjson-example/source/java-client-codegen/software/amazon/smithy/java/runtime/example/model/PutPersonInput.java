/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("name", PreludeSchemas.STRING,
            new RequiredTrait(),
            new HttpLabelTrait(),
            LengthTrait.builder().max(7L).build())
        .putMember("favoriteColor", PreludeSchemas.STRING,
            new HttpQueryTrait("favoriteColor"))
        .putMember("age", PreludeSchemas.INTEGER,
            new JsonNameTrait("Age"),
            new DefaultTrait(Node.from(0))    ,
            RangeTrait.builder().max(new BigDecimal("150")).build())
        .putMember("birthday", SharedSchemas.BIRTHDAY)
        .putMember("binary", PreludeSchemas.BLOB)
        .putMember("queryParams", SharedSchemas.MAP_LIST_STRING,
            new HttpQueryParamsTrait())
        .build();

    private static final Schema SCHEMA_NAME = SCHEMA.member("name");
    private static final Schema SCHEMA_FAVORITE_COLOR = SCHEMA.member("favoriteColor");
    private static final Schema SCHEMA_AGE = SCHEMA.member("age");
    private static final Schema SCHEMA_BIRTHDAY = SCHEMA.member("birthday");
    private static final Schema SCHEMA_BINARY = SCHEMA.member("binary");
    private static final Schema SCHEMA_QUERY_PARAMS = SCHEMA.member("queryParams");

    private transient final @NonNull String name;
    private transient final String favoriteColor;
    private transient final int age;
    private transient final Instant birthday;
    private transient final ByteBuffer binary;
    private transient final Map<String, List<String>> queryParams;

    private PutPersonInput(Builder builder) {
        this.name = builder.name;
        this.favoriteColor = builder.favoriteColor;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.binary = builder.binary == null ? null : builder.binary.asReadOnlyBuffer();
        this.queryParams = builder.queryParams == null ? null : Collections.unmodifiableMap(builder.queryParams);
    }

    public @NonNull String name() {
        return name;
    }

    public String favoriteColor() {
        return favoriteColor;
    }

    public int age() {
        return age;
    }

    public Instant birthday() {
        return birthday;
    }

    public ByteBuffer binary() {
        return binary;
    }

    public Map<String, List<String>> queryParams() {
        if (queryParams == null) {
            return Collections.emptyMap();
        }
        return queryParams;
    }

    public boolean hasQueryParams() {
        return queryParams != null;
    }

    @Override
    public @NonNull String toString() {
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
        PutPersonInput that = (PutPersonInput) other;
        return Objects.equals(this.name, that.name)
               && Objects.equals(this.favoriteColor, that.favoriteColor)
               && this.age == that.age
               && Objects.equals(this.birthday, that.birthday)
               && Objects.equals(this.binary, that.binary)
               && Objects.equals(this.queryParams, that.queryParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, favoriteColor, age, birthday, binary, queryParams);
    }

    @Override
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
        if (favoriteColor != null) {
            serializer.writeString(SCHEMA_FAVORITE_COLOR, favoriteColor);
        }
        serializer.writeInteger(SCHEMA_AGE, age);
        if (birthday != null) {
            serializer.writeTimestamp(SCHEMA_BIRTHDAY, birthday);
        }
        if (binary != null) {
            serializer.writeBlob(SCHEMA_BINARY, binary);
        }
        if (queryParams != null) {
            serializer.writeMap(SCHEMA_QUERY_PARAMS, queryParams, SharedSerde.MapListStringSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonInput}.
     */
    public static final class Builder implements ShapeBuilder<PutPersonInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String name;
        private String favoriteColor;
        private int age = 0;
        private Instant birthday;
        private ByteBuffer binary;
        private Map<String, List<String>> queryParams;

        private Builder() {}

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            tracker.setMember(SCHEMA_NAME);
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

        public Builder binary(ByteBuffer binary) {
            this.binary = binary;
            return this;
        }

        public Builder queryParams(Map<String, List<String>> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        @Override
        public @NonNull PutPersonInput build() {
            tracker.validate();
            return new PutPersonInput(this);
        }

        @Override
        public ShapeBuilder<PutPersonInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_NAME)) {
                name("");
            }
            return this;
        }

        @Override
        public Builder deserialize(@NonNull ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                    case 1 -> builder.favoriteColor(de.readString(member));
                    case 2 -> builder.age(de.readInteger(member));
                    case 3 -> builder.birthday(de.readTimestamp(member));
                    case 4 -> builder.binary(de.readBlob(member));
                    case 5 -> builder.queryParams(SharedSerde.deserializeMapListString(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.name(this.name);
        builder.favoriteColor(this.favoriteColor);
        builder.age(this.age);
        builder.birthday(this.birthday);
        builder.binary(this.binary);
        builder.queryParams(this.queryParams);
        return builder;
    }

}

