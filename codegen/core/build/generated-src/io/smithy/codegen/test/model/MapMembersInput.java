

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class MapMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#MapMembersInput");

    private static final Schema SCHEMA_REQUIRED_MAP = Schema.memberBuilder("requiredMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_MAP = Schema.memberBuilder("optionalMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DEFAULT_MAP = Schema.memberBuilder("defaultMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_MAP,
            SCHEMA_OPTIONAL_MAP,
            SCHEMA_DEFAULT_MAP
        )
        .build();

    private transient final Map<String, String> requiredMap;
    private transient final Map<String, String> optionalMap;
    private transient final Map<String, String> defaultMap;

    private MapMembersInput(Builder builder) {
        this.requiredMap = builder.requiredMap;
        this.optionalMap = builder.optionalMap;
        this.defaultMap = builder.defaultMap;
    }

    public Map<String, String> requiredMap() {
        return Collections.unmodifiableMap(requiredMap);
    }

    public boolean hasRequiredMap() {
        return true;
    }

    public Map<String, String> optionalMap() {
        if (optionalMap == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(optionalMap);
    }

    public boolean hasOptionalMap() {
        return optionalMap != null;
    }

    public Map<String, String> defaultMap() {
        return Collections.unmodifiableMap(defaultMap);
    }

    public boolean hasDefaultMap() {
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
        MapMembersInput that = (MapMembersInput) other;
        return Objects.equals(this.requiredMap, that.requiredMap)
               && Objects.equals(this.optionalMap, that.optionalMap)
               && Objects.equals(this.defaultMap, that.defaultMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredMap, optionalMap, defaultMap);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeMap(SCHEMA_REQUIRED_MAP, requiredMap, SharedSerde.MapStringStringSerializer.INSTANCE);

        if (optionalMap != null) {
            serializer.writeMap(SCHEMA_OPTIONAL_MAP, optionalMap, SharedSerde.MapStringStringSerializer.INSTANCE);
        }

        serializer.writeMap(SCHEMA_DEFAULT_MAP, defaultMap, SharedSerde.MapStringStringSerializer.INSTANCE);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MapMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<MapMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private Map<String, String> requiredMap;
        private Map<String, String> optionalMap;
        private Map<String, String> defaultMap = Collections.emptyMap();

        private Builder() {}

        public Builder requiredMap(Map<String, String> requiredMap) {
            this.requiredMap = Objects.requireNonNull(requiredMap, "requiredMap cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_MAP);
            return this;
        }

        public Builder optionalMap(Map<String, String> optionalMap) {
            this.optionalMap = optionalMap;
            return this;
        }

        public Builder defaultMap(Map<String, String> defaultMap) {
            this.defaultMap = Objects.requireNonNull(defaultMap, "defaultMap cannot be null");
            return this;
        }

        @Override
        public MapMembersInput build() {
            tracker.validate();
            return new MapMembersInput(this);
        }

        @Override
        public ShapeBuilder<MapMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_MAP)) {
                requiredMap(Collections.emptyMap());
            }
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredMap(SharedSerde.deserializeMapStringString(member, de));
                    case 1 -> builder.optionalMap(SharedSerde.deserializeMapStringString(member, de));
                    case 2 -> builder.defaultMap(SharedSerde.deserializeMapStringString(member, de));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredMap(this.requiredMap);
        builder.optionalMap(this.optionalMap);
        builder.defaultMap(this.defaultMap);
        return builder;
    }

}

