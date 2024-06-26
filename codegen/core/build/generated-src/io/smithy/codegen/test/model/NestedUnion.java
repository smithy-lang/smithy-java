

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public abstract class NestedUnion implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.common#NestedUnion");

    private static final Schema SCHEMA_A = Schema.memberBuilder("a", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final Schema SCHEMA_B = Schema.memberBuilder("b", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.UNION)
        .members(
            SCHEMA_A,
            SCHEMA_B
        )
        .build();

    private final Type type;

    private NestedUnion(Type type) {
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        $UNKNOWN,
        A,
        B
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public String a() {
        return null;
    }

    public Integer b() {
        return null;
    }

    @SmithyGenerated
    public static final class AMember extends NestedUnion {
        private final transient String value;

        public AMember(String value) {
            super(Type.A);
            this.value = Objects.requireNonNull(value, "Union value cannot be null");
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_A, value);
        }

        @Override
        public String a() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            AMember that = (AMember) other;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    @SmithyGenerated
    public static final class BMember extends NestedUnion {
        private final transient int value;

        public BMember(int value) {
            super(Type.B);
            this.value = value;
        }

        @Override
        public void serialize(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA, this);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA_B, value);
        }

        @Override
        public Integer b() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            BMember that = (BMember) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public interface BuildStage {
        NestedUnion build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NestedUnion}.
     */
    public static final class Builder implements ShapeBuilder<NestedUnion>, BuildStage {
        private NestedUnion value;

        private Builder() {}

        public BuildStage a(String value) {
            checkForExistingValue();
            this.value = new AMember(value);
            return this;
        }

        public BuildStage b(int value) {
            checkForExistingValue();
            this.value = new BMember(value);
            return this;
        }

        private void checkForExistingValue() {
            if (this.value != null) {
                throw new SerializationException("Only one value may be set for unions");
            }
        }

        @Override
        public NestedUnion build() {
            return Objects.requireNonNull(value, "no union value set");
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
                    case 0 -> builder.a(de.readString(member));
                    case 1 -> builder.b(de.readInteger(member));
                }
            }
        }

    }
}

