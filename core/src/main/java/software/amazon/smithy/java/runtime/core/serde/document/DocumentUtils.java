/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public final class DocumentUtils {

    private DocumentUtils() {}

    private static final Map<Class<? extends Number>, Schema> NUMBER_MAPPING = Map.of(
        AtomicLong.class,
        PreludeSchemas.LONG,
        AtomicInteger.class,
        PreludeSchemas.INTEGER,
        Byte.class,
        PreludeSchemas.BYTE,
        Short.class,
        PreludeSchemas.SHORT,
        Integer.class,
        PreludeSchemas.INTEGER,
        Long.class,
        PreludeSchemas.LONG,
        Float.class,
        PreludeSchemas.FLOAT,
        Double.class,
        PreludeSchemas.DOUBLE,
        BigInteger.class,
        PreludeSchemas.BIG_INTEGER,
        BigDecimal.class,
        PreludeSchemas.BIG_DECIMAL
    );

    public static void serializeNumber(ShapeSerializer serializer, Schema schema, Number value) {
        switch (schema.type()) {
            case BYTE -> serializer.writeByte(schema, value.byteValue());
            case SHORT -> serializer.writeShort(schema, value.shortValue());
            case INTEGER -> serializer.writeInteger(schema, value.intValue());
            case LONG -> serializer.writeLong(schema, value.longValue());
            case FLOAT -> serializer.writeFloat(schema, value.floatValue());
            case DOUBLE -> serializer.writeDouble(schema, value.doubleValue());
            case BIG_INTEGER -> serializer.writeBigInteger(schema, toBigInteger(value));
            case BIG_DECIMAL -> serializer.writeBigDecimal(schema, toBigDecimal(value));
            default -> throw new UnsupportedOperationException("Unsupported numeric type: " + schema.type());
        }
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal b) {
            return b;
        } else if (number instanceof BigInteger b) {
            return new BigDecimal(b);
        } else if (number instanceof Integer || number instanceof Long
            || number instanceof Byte || number instanceof Short) {
                return BigDecimal.valueOf(number.longValue());
            } else {
                return BigDecimal.valueOf(number.doubleValue());
            }
    }

    private static BigInteger toBigInteger(Number number) {
        if (number instanceof BigInteger b) {
            return b;
        } else if (number instanceof BigDecimal b) {
            return b.toBigInteger();
        } else {
            return BigInteger.valueOf(number.longValue());
        }
    }

    public static Schema getSchemaForNumber(Number value) {
        var result = NUMBER_MAPPING.get(value.getClass());
        // Note that BigInteger and BigDecimal can be extended.
        if (result != null) {
            return result;
        } else if (value instanceof BigInteger) {
            return PreludeSchemas.BIG_INTEGER;
        } else if (value instanceof BigDecimal) {
            return PreludeSchemas.BIG_DECIMAL;
        } else {
            throw new IllegalArgumentException(
                String.format(
                    "Unsupported Number: %s; expected one of %s",
                    value.getClass(),
                    NUMBER_MAPPING.keySet()
                )
            );
        }
    }
}
