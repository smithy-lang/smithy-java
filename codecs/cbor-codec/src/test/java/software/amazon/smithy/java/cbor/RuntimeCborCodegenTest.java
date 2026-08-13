/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

final class RuntimeCborCodegenTest {
    @Test
    void directlyRoundTripsScalarStructure() {
        var serde = new SmithyGeneratedCborSerde();
        var value = ScalarStruct.builder()
                .name("test-\u00e9")
                .count(42)
                .enabled(true)
                .score(1.5)
                .build();

        ByteBuffer encoded = serde.serialize(value, CborSettings.defaultSettings());
        assertThat(encoded).isNotNull();
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);

        assertThat(serde.deserialize(bytes, ScalarStruct.builder(), CborSettings.defaultSettings()))
                .isEqualTo(value);
        assertThat(serde.diagnostics().successes()).isEqualTo(1);
    }

    @Test
    void integratesOnlyWhenFeatureGateIsEnabled() {
        String previous = System.getProperty("smithy-java.runtime-codegen");
        System.setProperty("smithy-java.runtime-codegen", "cbor");
        try {
            var codec = Rpcv2CborCodec.builder().build();
            var value = ScalarStruct.builder().name("value").count(7).build();
            ByteBuffer encoded = codec.serialize(value);
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);

            assertThat(codec.deserializeShape(bytes, ScalarStruct.builder())).isEqualTo(value);
        } finally {
            if (previous == null) {
                System.clearProperty("smithy-java.runtime-codegen");
            } else {
                System.setProperty("smithy-java.runtime-codegen", previous);
            }
        }
    }

    @Test
    void cachesUnsupportedGraphAndFallsBack() {
        var serde = new SmithyGeneratedCborSerde();
        var value = AggregateStruct.builder().values(List.of("a", "b")).build();

        assertThat(serde.serialize(value, CborSettings.defaultSettings())).isNull();
        assertThat(serde.serialize(value, CborSettings.defaultSettings())).isNull();
        assertThat(serde.diagnostics().failures()).isEqualTo(1);
        assertThat(serde.diagnostics().fallbacks()).isEqualTo(2);

        String previous = System.getProperty("smithy-java.runtime-codegen");
        System.setProperty("smithy-java.runtime-codegen", "cbor");
        try {
            assertThat(Rpcv2CborCodec.builder().build().serialize(value)).isNotNull();
        } finally {
            if (previous == null) {
                System.clearProperty("smithy-java.runtime-codegen");
            } else {
                System.setProperty("smithy-java.runtime-codegen", previous);
            }
        }
    }

    public static final class ScalarStruct implements SerializableStruct {
        private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.cbor#ScalarStruct"))
                .shapeClass(ScalarStruct.class)
                .builderSupplier(Builder::new)
                .putMember("name", PreludeSchemas.STRING)
                .putMember("count", PreludeSchemas.INTEGER)
                .putMember("enabled", PreludeSchemas.BOOLEAN)
                .putMember("score", PreludeSchemas.DOUBLE)
                .build();
        private final String name;
        private final int count;
        private final boolean enabled;
        private final double score;

        private ScalarStruct(Builder builder) {
            name = builder.name;
            count = builder.count;
            enabled = builder.enabled;
            score = builder.score;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getScore() {
            return score;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (name != null) {
                serializer.writeString(SCHEMA.member("name"), name);
            }
            serializer.writeInteger(SCHEMA.member("count"), count);
            serializer.writeBoolean(SCHEMA.member("enabled"), enabled);
            serializer.writeDouble(SCHEMA.member("score"), score);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) switch (member.memberIndex()) {
                case 0 -> name;
                case 1 -> count;
                case 2 -> enabled;
                case 3 -> score;
                default -> null;
            };
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ScalarStruct that
                    && count == that.count
                    && enabled == that.enabled
                    && Double.compare(score, that.score) == 0
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, count, enabled, score);
        }

        public static final class Builder implements ShapeBuilder<ScalarStruct> {
            private String name;
            private int count;
            private boolean enabled;
            private double score;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder count(int count) {
                this.count = count;
                return this;
            }

            public Builder enabled(boolean enabled) {
                this.enabled = enabled;
                return this;
            }

            public Builder score(double score) {
                this.score = score;
                return this;
            }

            @Override
            public ScalarStruct build() {
                return new ScalarStruct(this);
            }

            @Override
            public ShapeBuilder<ScalarStruct> deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, reader) -> {
                    switch (member.memberIndex()) {
                        case 0 -> builder.name(reader.readString(member));
                        case 1 -> builder.count(reader.readInteger(member));
                        case 2 -> builder.enabled(reader.readBoolean(member));
                        case 3 -> builder.score(reader.readDouble(member));
                        default -> {
                        }
                    }
                });
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }

    public static final class AggregateStruct implements SerializableStruct {
        private static final Schema LIST = Schema.listBuilder(ShapeId.from("codegen.cbor#StringList"))
                .putMember("member", PreludeSchemas.STRING)
                .build();
        private static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("codegen.cbor#AggregateStruct"))
                .shapeClass(AggregateStruct.class)
                .builderSupplier(Builder::new)
                .putMember("values", LIST)
                .build();
        private final List<String> values;

        private AggregateStruct(Builder builder) {
            values = builder.values;
        }

        public static Builder builder() {
            return new Builder();
        }

        public List<String> getValues() {
            return values;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (values != null) {
                serializer.writeList(SCHEMA.member("values"), values, values.size(), (items, writer) -> {
                    for (String value : items) {
                        writer.writeString(LIST.listMember(), value);
                    }
                });
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) values;
        }

        public static final class Builder implements ShapeBuilder<AggregateStruct> {
            private List<String> values;

            public Builder values(List<String> values) {
                this.values = values;
                return this;
            }

            @Override
            public AggregateStruct build() {
                return new AggregateStruct(this);
            }

            @Override
            public ShapeBuilder<AggregateStruct> deserialize(ShapeDeserializer decoder) {
                return this;
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }
        }
    }
}
