/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

final class CborTestData {
    static final ShapeId BIRD_ID = ShapeId.from("smithy.example#Bird");
    static final Schema BIRD = Schema.structureBuilder(BIRD_ID)
            .putMember("name", PreludeSchemas.STRING)
            .putMember("bytes", PreludeSchemas.BLOB)
            .build();
    static final Schema BIRD_NAME = BIRD.member("name");
    static final Schema BIRD_BYTES = BIRD.member("bytes");

    static final class Bird implements SerializableStruct {
        String name;
        ByteBuffer bytes;

        private Bird(BirdBuilder builder) {
            name = builder.name;
            bytes = builder.bytes;
        }

        @Override
        public Schema schema() {
            return CborTestData.BIRD;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            if (name != null) {
                serializer.writeString(BIRD_NAME, name);
            }
            if (bytes != null) {
                serializer.writeBlob(BIRD_BYTES, bytes);
            }
        }

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }

    static final class BirdBuilder implements ShapeBuilder<Bird> {
        private String name;
        private ByteBuffer bytes;

        @Override
        public Bird build() {
            return new Bird(this);
        }

        @Override
        public ShapeBuilder<Bird> deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(schema(), this, (builder, member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                    case 1 -> builder.bytes(de.readBlob(member));
                }
            });
            return this;
        }

        BirdBuilder name(String name) {
            this.name = name;
            return this;
        }

        BirdBuilder bytes(ByteBuffer bytes) {
            this.bytes = bytes;
            return this;
        }

        @Override
        public Schema schema() {
            return CborTestData.BIRD;
        }
    }
}
