/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.xml;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

public class XmlCodecTest {
    @Test
    public void deserializesXml() {
        var xml = """
            <Foo>
                <date>2006-03-01T00:00:00Z</date>
                <name>Hello</name>
                <numbers>
                    <member>1</member>
                    <member>2</member>
                    <member>3</member>
                </numbers>
            </Foo>
            """;

        try (var codec = XmlCodec.builder().build()) {
            var pojo = codec.deserializeShape(xml, new TestPojo.Builder());
            assertThat(pojo.name, equalTo("Hello"));
            assertThat(pojo.date, equalTo(Instant.parse("2006-03-01T00:00:00Z")));
            assertThat(pojo.numbers, contains(1, 2, 3));
        }
    }

    @Test
    public void serializesXml() {
        try (var codec = XmlCodec.builder().build()) {
            var builder = new TestPojo.Builder();
            builder.name = "Hello";
            builder.date = Instant.parse("2006-03-01T00:00:00Z");
            builder.numbers.addAll(List.of(1, 2, 3));
            var pojo = builder.build();

            var xml = codec.serializeToString(pojo);
            assertThat(
                xml,
                equalTo(
                    "<Foo><name>Hello</name><date>2006-03-01T00:00:00Z</date><numbers><member>1</member><member>2</member><member>3</member></numbers></Foo>"
                )
            );
        }
    }

    private static final class TestPojo implements SerializableStruct {

        private static final ShapeId ID = ShapeId.from("smithy.example#Foo");

        private static final Schema NUMBERS_LIST = Schema.listBuilder(ShapeId.from("smithy.example#Numbers"))
            .putMember("member", PreludeSchemas.INTEGER)
            .build();

        private static final Schema SCHEMA = Schema.structureBuilder(ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("binary", PreludeSchemas.BLOB, new JsonNameTrait("BINARY"))
            .putMember("date", PreludeSchemas.TIMESTAMP, new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME))
            .putMember("numbers", NUMBERS_LIST)
            .build();

        private static final Schema NAME = SCHEMA.member("name");
        private static final Schema BINARY = SCHEMA.member("binary");
        private static final Schema DATE = SCHEMA.member("date");
        private static final Schema NUMBERS = SCHEMA.member("numbers");

        private final String name;
        private final ByteBuffer binary;
        private final Instant date;
        private final List<Integer> numbers;

        TestPojo(Builder builder) {
            this.name = builder.name;
            this.binary = builder.binary;
            this.date = builder.date;
            this.numbers = builder.numbers;
        }

        @Override
        public Schema schema() {
            return SCHEMA;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (name != null) {
                serializer.writeString(NAME, name);
            }
            if (binary != null) {
                serializer.writeBlob(BINARY, binary);
            }
            if (date != null) {
                serializer.writeTimestamp(DATE, date);
            }
            if (numbers != null) {
                serializer.writeList(NUMBERS, numbers, numbers.size(), (elements, ser) -> {
                    for (var e : elements) {
                        ser.writeInteger(PreludeSchemas.INTEGER, e);
                    }
                });
            }
        }

        @Override
        public Object getMemberValue(Schema member) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        private static final class Builder implements ShapeBuilder<TestPojo> {

            private String name;
            private ByteBuffer binary;
            private Instant date;
            private final List<Integer> numbers = new ArrayList<>();

            @Override
            public Schema schema() {
                return SCHEMA;
            }

            @Override
            public Builder deserialize(ShapeDeserializer decoder) {
                decoder.readStruct(SCHEMA, this, (pojo, member, deser) -> {
                    switch (member.memberName()) {
                        case "name" -> pojo.name = deser.readString(NAME);
                        case "binary" -> pojo.binary = deser.readBlob(BINARY);
                        case "date" -> pojo.date = deser.readTimestamp(DATE);
                        case "numbers" -> {
                            deser.readList(NUMBERS, pojo.numbers, (values, de) -> {
                                values.add(de.readInteger(NUMBERS_LIST.member("member")));
                            });
                        }
                        default -> throw new UnsupportedOperationException(member.toString());
                    }
                });
                return this;
            }

            @Override
            public TestPojo build() {
                return new TestPojo(this);
            }
        }
    }
}
