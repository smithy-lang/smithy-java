/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.document;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

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

    // Emulate JLS 5.1.2 type promotion.
    static boolean compareWithPromotion(Number a, Document rightDocument) {
        try {
            return compareWithPromotion(a, rightDocument.asNumber());
        } catch (SerializationException e) {
            // If the other document can't be converted to a number, then don't fail. Instead, report false.
            return false;
        }
    }

    // Emulate JLS 5.1.2 type promotion.
    private static boolean compareWithPromotion(Number a, Number b) {
        // Exact matches.
        if (a.equals(b)) {
            return true;
        } else if (isBig(a, b)) {
            // When the values have a BigDecimal or BigInteger, normalize them both to BigDecimal. This is used even
            // for BigInteger to avoid dropping decimals from doubles or floats (e.g., 10.01 != 10).
            return DocumentUtils.toBigDecimal(a)
                .stripTrailingZeros()
                .compareTo(DocumentUtils.toBigDecimal(b).stripTrailingZeros()) == 0;
        } else if (a instanceof Double || b instanceof Double || a instanceof Float || b instanceof Float) {
            // Treat floats as double to allow for comparing larger values from rhs, like longs.
            return a.doubleValue() == b.doubleValue();
        } else {
            return a.longValue() == b.longValue();
        }
    }

    private static boolean isBig(Number a, Number b) {
        return a instanceof BigDecimal || b instanceof BigDecimal
            || a instanceof BigInteger || b instanceof BigInteger;
    }
}
