

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
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
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class MapsInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#MapsInput");

    private static final SdkSchema SCHEMA_REQUIRED_MAP = SdkSchema.memberBuilder("requiredMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_MAP = SdkSchema.memberBuilder("optionalMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_MAP = SdkSchema.memberBuilder("defaultMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
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

    private MapsInput(Builder builder) {
        this.requiredMap = Collections.unmodifiableMap(builder.requiredMap);
        this.optionalMap = builder.optionalMap != null ? Collections.unmodifiableMap(builder.optionalMap) : null;
        this.defaultMap = Collections.unmodifiableMap(builder.defaultMap);
    }

    public Map<String, String> requiredMap() {
        return requiredMap;
    }

    public boolean hasRequiredMap() {
        return requiredMap != null;
    }

    public Map<String, String> optionalMap() {
        return optionalMap != null ? optionalMap : Collections.emptyMap();
    }

    public boolean hasOptionalMap() {
        return optionalMap != null;
    }

    public Map<String, String> defaultMap() {
        return defaultMap;
    }

    public boolean hasDefaultMap() {
        return defaultMap != null;
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
        MapsInput that = (MapsInput) other;
        return Objects.equals(requiredMap, that.requiredMap)
               && Objects.equals(optionalMap, that.optionalMap)
               && Objects.equals(defaultMap, that.defaultMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredMap, optionalMap, defaultMap);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<MapsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(MapsInput shape, ShapeSerializer serializer) {
            serializer.writeMap(SCHEMA_REQUIRED_MAP, shape.requiredMap, SharedSchemas.MapStringStringSerializer.INSTANCE);
            if (shape.optionalMap != null) {
                serializer.writeMap(SCHEMA_OPTIONAL_MAP, shape.optionalMap, SharedSchemas.MapStringStringSerializer.INSTANCE);
            }
            serializer.writeMap(SCHEMA_DEFAULT_MAP, shape.defaultMap, SharedSchemas.MapStringStringSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link MapsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<MapsInput> {
        private Map<String, String> requiredMap;
        private Map<String, String> optionalMap;
        private Map<String, String> defaultMap = new LinkedHashMap<>();

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredMap(Map<String, String> requiredMap) {
            this.requiredMap = new LinkedHashMap<>(Objects.requireNonNull(requiredMap, "requiredMap cannot be null"));
            tracker.setMember(SCHEMA_REQUIRED_MAP);
            return this;
        }

        public Builder putAllRequiredMap(Map<String, String> requiredMap) {
            if (this.requiredMap == null) {
                this.requiredMap = new LinkedHashMap<>(requiredMap);
                tracker.setMember(SCHEMA_REQUIRED_MAP);
            } else {
                this.requiredMap.putAll(requiredMap);
            }
            return this;
        }

        public Builder putRequiredMap(String key, String value) {
           if (this.requiredMap == null) {
               this.requiredMap = new LinkedHashMap<>();
               tracker.setMember(SCHEMA_REQUIRED_MAP);
           }
           this.requiredMap.put(key, value);
           return this;
        }

        public Builder optionalMap(Map<String, String> optionalMap) {
            this.optionalMap = optionalMap != null ? new LinkedHashMap<>(optionalMap) : null;
            return this;
        }

        public Builder putAllOptionalMap(Map<String, String> optionalMap) {
            if (this.optionalMap == null) {
                this.optionalMap = new LinkedHashMap<>(optionalMap);
            } else {
                this.optionalMap.putAll(optionalMap);
            }
            return this;
        }

        public Builder putOptionalMap(String key, String value) {
           if (this.optionalMap == null) {
               this.optionalMap = new LinkedHashMap<>();
           }
           this.optionalMap.put(key, value);
           return this;
        }

        public Builder defaultMap(Map<String, String> defaultMap) {
            this.defaultMap = defaultMap != null ? new LinkedHashMap<>(defaultMap) : null;
            return this;
        }

        public Builder putAllDefaultMap(Map<String, String> defaultMap) {
            if (this.defaultMap == null) {
                this.defaultMap = new LinkedHashMap<>(defaultMap);
            } else {
                this.defaultMap.putAll(defaultMap);
            }
            return this;
        }

        public Builder putDefaultMap(String key, String value) {
           if (this.defaultMap == null) {
               this.defaultMap = new LinkedHashMap<>();
           }
           this.defaultMap.put(key, value);
           return this;
        }

        @Override
        public MapsInput build() {
            tracker.validate();
            return new MapsInput(this);
        }

        @Override
        public SdkShapeBuilder<MapsInput> errorCorrection() {
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredMap(SharedSchemas.deserializeMapStringString(member, de));
                    case 1 -> builder.optionalMap(SharedSchemas.deserializeMapStringString(member, de));
                    case 2 -> builder.defaultMap(SharedSchemas.deserializeMapStringString(member, de));
                }
            }
        }
    }
}

