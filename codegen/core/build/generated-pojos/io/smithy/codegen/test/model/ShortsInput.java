

package io.smithy.codegen.test.model;

import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
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
public final class ShortsInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#ShortsInput");

    private static final SdkSchema SCHEMA_REQUIRED_SHORT = SdkSchema.memberBuilder("requiredShort", PreludeSchemas.SHORT)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_SHORT = SdkSchema.memberBuilder("optionalShort", PreludeSchemas.SHORT)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_SHORT = SdkSchema.memberBuilder("defaultShort", PreludeSchemas.SHORT)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_SHORT,
            SCHEMA_OPTIONAL_SHORT,
            SCHEMA_DEFAULT_SHORT
        )
        .build();

    private transient final short requiredShort;
    private transient final Short optionalShort;
    private transient final short defaultShort;

    private ShortsInput(Builder builder) {
        this.requiredShort = builder.requiredShort;
        this.optionalShort = builder.optionalShort;
        this.defaultShort = builder.defaultShort;
    }

    public Short requiredShort() {
        return requiredShort;
    }

    public short optionalShort() {
        return optionalShort;
    }

    public Short defaultShort() {
        return defaultShort;
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
        ShortsInput that = (ShortsInput) other;
        return requiredShort == that.requiredShort
               && Objects.equals(optionalShort, that.optionalShort)
               && defaultShort == that.defaultShort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredShort, optionalShort, defaultShort);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<ShortsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(ShortsInput shape, ShapeSerializer serializer) {
            serializer.writeShort(SCHEMA_REQUIRED_SHORT, shape.requiredShort);
            if (shape.optionalShort != null) {
                serializer.writeShort(SCHEMA_OPTIONAL_SHORT, shape.optionalShort);
            }
            serializer.writeShort(SCHEMA_DEFAULT_SHORT, shape.defaultShort);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ShortsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<ShortsInput> {
        private short requiredShort;
        private Short optionalShort;
        private short defaultShort = 1;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredShort(short requiredShort) {
            this.requiredShort = requiredShort;
            tracker.setMember(SCHEMA_REQUIRED_SHORT);
            return this;
        }

        public Builder optionalShort(short optionalShort) {
            this.optionalShort = optionalShort;
            return this;
        }

        public Builder defaultShort(short defaultShort) {
            this.defaultShort = defaultShort;
            return this;
        }

        @Override
        public ShortsInput build() {
            tracker.validate();
            return new ShortsInput(this);
        }

        @Override
        public SdkShapeBuilder<ShortsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_SHORT)) {
                tracker.setMember(SCHEMA_REQUIRED_SHORT);
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
                    case 0 -> builder.requiredShort(de.readShort(member));
                    case 1 -> builder.optionalShort(de.readShort(member));
                    case 2 -> builder.defaultShort(de.readShort(member));
                }
            }
        }
    }
}

