/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
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
            .putMember("lastSquawkAt", PreludeSchemas.TIMESTAMP)
            .putMember("flightRange", PreludeSchemas.BIG_INTEGER)
            .putMember("wingspan", PreludeSchemas.BIG_DECIMAL)
            .build();
    static final Schema BIRD_NAME = BIRD.member("name");
    static final Schema BIRD_BYTES = BIRD.member("bytes");
    static final Schema BIRD_LAST_SQUAWK_AT = BIRD.member("lastSquawkAt");
    static final Schema BIRD_FLIGHT_RANGE = BIRD.member("flightRange");
    static final Schema BIRD_WING_SPAN = BIRD.member("wingspan");

    static final class Bird implements SerializableStruct {
        String name;
        ByteBuffer bytes;
        Instant lastSquawkAt;
        BigInteger flightRange;
        BigDecimal wingspan;

        private Bird(BirdBuilder builder) {
            name = builder.name;
            bytes = builder.bytes;
            lastSquawkAt = builder.lastSquawkAt;
            flightRange = builder.flightRange;
            wingspan = builder.wingspan;
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
            if (lastSquawkAt != null) {
                serializer.writeTimestamp(BIRD_LAST_SQUAWK_AT, lastSquawkAt);
            }
            if (flightRange != null) {
                serializer.writeBigInteger(BIRD_FLIGHT_RANGE, flightRange);
            }
            if (wingspan != null) {
                serializer.writeBigDecimal(BIRD_WING_SPAN, wingspan);
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
        private Instant lastSquawkAt;
        private BigInteger flightRange;
        private BigDecimal wingspan;

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
                    case 2 -> builder.lastSquawkAt(de.readTimestamp(member));
                    case 3 -> builder.flightRange(de.readBigInteger(member));
                    case 4 -> builder.wingspan(de.readBigDecimal(member));
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

        BirdBuilder lastSquawkAt(Instant lastSquawkAt) {
            this.lastSquawkAt = lastSquawkAt;
            return this;
        }

        BirdBuilder flightRange(BigInteger flightRange) {
            this.flightRange = flightRange;
            return this;
        }

        BirdBuilder wingspan(BigDecimal wingspan) {
            this.wingspan = wingspan;
            return this;
        }

        @Override
        public Schema schema() {
            return CborTestData.BIRD;
        }
    }
}
