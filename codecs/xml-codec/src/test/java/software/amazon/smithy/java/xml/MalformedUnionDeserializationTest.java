/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Malformed union payloads (no member set, or more than one member set) must surface as
 * {@link SerializationException} regardless of the XML provider, so that callers can map them to
 * client errors (e.g. HTTP 400). The union builder itself throws {@link NullPointerException} /
 * {@link IllegalArgumentException} for these cases, which must not escape the codec.
 */
public class MalformedUnionDeserializationTest extends ProviderTestBase {

    static Stream<Arguments> malformedUnions() {
        List<Named<String>> payloads = List.of(
                Named.of("nested union with no members",
                        "<Wrapper><choice></choice></Wrapper>"),
                Named.of("nested union with two members",
                        "<Wrapper><choice><stringValue>a</stringValue><intValue>1</intValue></choice></Wrapper>"));
        return providers().stream()
                .flatMap(provider -> payloads.stream()
                        .map(payload -> Arguments.of(provider.get()[0], payload)));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource
    void malformedUnions(boolean useNative, String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        try (var codec = codec(useNative)) {
            assertSerializationException(payload, () -> codec.deserializeShape(bytes, new Wrapper.Builder()));
            assertSerializationException(payload,
                    () -> codec.deserializeShape(ByteBuffer.wrap(bytes), new Wrapper.Builder()));
        }
    }

    private static void assertSerializationException(String payload, Executable executable) {
        var e = assertThrows(Throwable.class, executable);
        assertThat(e)
                .as("Expected SerializationException for %s but got %s", payload, e)
                .isInstanceOf(SerializationException.class);
    }

    // Mirrors the code generated for `union TestUnion { stringValue: String, intValue: Integer }`.
    private sealed interface TestUnion extends SerializableStruct {
        Schema SCHEMA = Schema.unionBuilder(ShapeId.from("smithy.example#TestUnion"))
                .putMember("stringValue", PreludeSchemas.STRING)
                .putMember("intValue", PreludeSchemas.INTEGER)
                .builderSupplier(Builder::new)
                .build();

        @Override
        default Schema schema() {
            return SCHEMA;
        }

        @Override
        default void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        default <T> T getMemberValue(Schema member) {
            return null;
        }

        record StringValue(String value) implements TestUnion {}

        record IntValue(int value) implements TestUnion {}

        record Unknown(String memberName) implements TestUnion {}

        final class Builder implements ShapeBuilder<TestUnion> {
            private TestUnion value;

            private void setValue(TestUnion value) {
                if (this.value != null) {
                    throw new IllegalArgumentException("Only one value may be set for unions");
                }
                this.value = value;
            }

            @Override
            public TestUnion build() {
                return Objects.requireNonNull(value, "no union value set");
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
                return this;
            }

            @Override
            public Builder deserializeMember(ShapeDeserializer decoder, Schema schema) {
                decoder.readStruct(schema.assertMemberTargetIs(SCHEMA), this, InnerDeserializer.INSTANCE);
                return this;
            }

            private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
                private static final InnerDeserializer INSTANCE = new InnerDeserializer();

                @Override
                public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                    switch (member.memberIndex()) {
                        case 0 -> builder.setValue(new StringValue(de.readString(member)));
                        case 1 -> builder.setValue(new IntValue(de.readInteger(member)));
                        default -> throw new IllegalArgumentException("Unexpected member: " + member.memberName());
                    }
                }

                @Override
                public void unknownMember(Builder builder, String memberName) {
                    builder.setValue(new Unknown(memberName));
                }
            }
        }
    }

    private record Wrapper(TestUnion choice) implements SerializableStruct {
        static final Schema SCHEMA = Schema.structureBuilder(ShapeId.from("smithy.example#Wrapper"))
                .putMember("choice", TestUnion.SCHEMA)
                .builderSupplier(Builder::new)
                .build();

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }

        private static final class Builder implements ShapeBuilder<Wrapper> {
            private TestUnion choice;

            @Override
            public Wrapper build() {
                return new Wrapper(choice);
            }

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                    if (member.memberIndex() == 0) {
                        builder.choice = new TestUnion.Builder().deserializeMember(de, member).build();
                    }
                });
                return this;
            }
        }
    }
}
