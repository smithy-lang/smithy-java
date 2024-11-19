/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.model.traits.HttpQueryTrait;

final class HttpQuerySerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> queryWriter;

    public HttpQuerySerializer(BiConsumer<String, String> queryWriter) {
        this.queryWriter = queryWriter;
    }

    @Override
    public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        consumer.accept(listState, this);
    }

    private void writeQuery(HttpQueryTrait trait, String value) {
        queryWriter.accept(trait.getValue(), value);
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Boolean.toString(value));
        }
    }

    @Override
    public void writeShort(Schema schema, short value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Short.toString(value));
        }
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Byte.toString(value));
        }
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Integer.toString(value));
        }
    }

    @Override
    public void writeLong(Schema schema, long value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Long.toString(value));
        }
    }

    @Override
    public void writeFloat(Schema schema, float value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Float.toString(value));
        }
    }

    @Override
    public void writeDouble(Schema schema, double value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, Double.toString(value));
        }
    }

    @Override
    public void writeBigInteger(Schema schema, BigInteger value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, value.toString());
        }
    }

    @Override
    public void writeBigDecimal(Schema schema, BigDecimal value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, value.toString());
        }
    }

    @Override
    public void writeString(Schema schema, String value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, value);
        }
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            writeQuery(queryTrait, ByteBufferUtils.base64Encode(value));
        }
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        var queryTrait = schema.getTrait(TraitKey.HTTP_QUERY_TRAIT);
        if (queryTrait != null) {
            var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
            TimestampFormatter formatter = trait != null
                ? TimestampFormatter.of(trait)
                : TimestampFormatter.Prelude.DATE_TIME;
            writeQuery(queryTrait, formatter.writeString(value));
        }
    }
}
