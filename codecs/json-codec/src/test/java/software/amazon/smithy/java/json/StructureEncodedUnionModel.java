/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class StructureEncodedUnionModel {
    private StructureEncodedUnionModel() {}

    public sealed interface Value extends SerializableStruct permits Value.SMember, Value.$Unknown {
        Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.json#StructureEncodedUnion"))
                .shapeClass(Value.class)
                .builderSupplier(Builder::new)
                .putMember("S", PreludeSchemas.STRING)
                .build();

        static Builder builder() {
            return new Builder();
        }

        @Override
        default Schema schema() {
            return SCHEMA;
        }

        @Override
        @SuppressWarnings("unchecked")
        default <T> T getMemberValue(Schema member) {
            return (T) ((SMember) this).s();
        }

        record SMember(String s) implements Value {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {
                serializer.writeString(SCHEMA.member("S"), s);
            }
        }

        record $Unknown(String memberName) implements Value {
            @Override
            public void serializeMembers(ShapeSerializer serializer) {}
        }

        final class Builder implements ShapeBuilder<Value> {
            private Value value;

            public Builder s(String value) {
                this.value = new SMember(value);
                return this;
            }

            @Override
            public Value build() {
                return value;
            }

            @Override
            public ShapeBuilder<Value> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public static final class Envelope implements SerializableStruct {
        private static final Schema SCHEMA =
                Schema.structureBuilder(ShapeId.from("codegen.json#StructureUnionEnvelope"))
                        .shapeClass(Envelope.class)
                        .builderSupplier(Builder::new)
                        .putMember("attribute", Value.SCHEMA)
                        .build();
        private final Value attribute;

        private Envelope(Builder builder) {
            attribute = builder.attribute;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Value getAttribute() {
            return attribute;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            serializer.writeStruct(SCHEMA.member("attribute"), attribute);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) attribute;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Envelope that && Objects.equals(attribute, that.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attribute);
        }

        public static final class Builder implements ShapeBuilder<Envelope> {
            private Value attribute;

            public Builder attribute(Value attribute) {
                this.attribute = attribute;
                return this;
            }

            @Override
            public Envelope build() {
                return new Envelope(this);
            }

            @Override
            public ShapeBuilder<Envelope> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }
}
