/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Defines the serialization properties of a format.
 *
 * <p>Timestamp format implies an explicit formatting of a timestamp that isn't an abstraction.
 * Because of this, we can generically implement the parsing and serialization properties of a format behind an
 * interface. This interface currently requires that the value given to a format is either a string or a number.
 */
public interface TimestampFormatter {

    /**
     * Create a formatter from a timestamp format trait and the known prelude formats for date-time, epoch-seconds,
     * and http-date.
     *
     * @param trait Trait to create the format from.
     * @return Returns the created formatter.
     * @throws SdkSerdeException for an unknown format.
     */
    static TimestampFormatter of(TimestampFormatTrait trait) {
        return switch (trait.getFormat()) {
            case DATE_TIME -> Prelude.DATE_TIME;
            case EPOCH_SECONDS -> Prelude.EPOCH_SECONDS;
            case HTTP_DATE -> Prelude.HTTP_DATE;
            default -> throw new SdkSerdeException("Unknown timestamp format: " + trait.getFormat());
        };
    }

    /**
     * Get the modeled timestamp format type.
     *
     * @return Return the format type.
     */
    TimestampFormatTrait.Format format();

    /**
     * Format the given Instant to a String.
     *
     * <p>If the instant normally serializes into a number, this method must return that number as a string.
     *
     * @param value Value to format.
     * @return Returns the formatted string.
     */
    String writeString(Instant value);

    /**
     * Writes the timestamp value to given serializer using the underlying data format for the timestamp.
     *
     * <p>This method must not attempt to write a timestamp as that will cause infinite recursion. Instead, it
     * should serialize the timestamp as a string or number to the given serializer. In other words: don't call
     * writeTimestamp on the given serializer.
     *
     * @param schema     Schema of the timestamp.
     * @param value      Timestamp value to serialize.
     * @param serializer Where to serialize the data.
     */
    void writeToSerializer(SdkSchema schema, Instant value, ShapeSerializer serializer);

    /**
     * Parse a timestamp from a string.
     *
     * <p>This method must be able to parse the output of {@link #writeString(Instant)}.
     *
     * @param value Value to parse.
     * @param strict Set to true to throw if the string value comes from a serialization format that should
     *               serialize this format as a number.
     * @return Returns the created Instant.
     * @throws TimestampSyntaxError if the timestamp is not the right type or format.
     */
    Instant readFromString(String value, boolean strict);

    /**
     * Create the timestamp from a number, if possible.
     *
     * @param value Value to convert into a timestamp.
     * @return Returns the created Instant.
     * @throws TimestampSyntaxError if the timestamp is not the right type or format.
     */
    Instant readFromNumber(Number value);

    /**
     * Formats built into the Smithy prelude.
     */
    enum Prelude implements TimestampFormatter {
        EPOCH_SECONDS {
            @Override
            public TimestampFormatTrait.Format format() {
                return TimestampFormatTrait.Format.EPOCH_SECONDS;
            }

            @Override
            public String writeString(Instant value) {
                return String.format("%.3f", ((double) value.toEpochMilli()) / 1000);
            }

            @Override
            public Instant readFromString(String value, boolean strict) {
                if (strict) {
                    throw new TimestampSyntaxError(format(), ExpectedType.NUMBER, value);
                }
                return Instant.ofEpochMilli((long) (Double.parseDouble(value) * 1000));
            }

            @Override
            public Instant readFromNumber(Number value) {
                // The most common types for serialized epoch-seconds, double/integer/long, are checked first.
                if (value instanceof Double f) {
                    return Instant.ofEpochMilli((long) (f * 1000f));
                } else if (value instanceof Integer i) {
                    return Instant.ofEpochMilli(i * 1000L);
                } else if (value instanceof Long l) {
                    return Instant.ofEpochMilli(l * 1000L);
                } else if (value instanceof Byte b) {
                    return Instant.ofEpochMilli(b * 1000L);
                } else if (value instanceof Short s) {
                    return Instant.ofEpochMilli(s * 1000L);
                } else if (value instanceof Float f) {
                    return Instant.ofEpochMilli((long) (f * 1000f));
                } else if (value instanceof BigInteger bi) {
                    return Instant.ofEpochMilli(bi.longValue() * 1000);
                } else if (value instanceof BigDecimal bd) {
                    return Instant.ofEpochMilli(bd.longValue() * 1000);
                } else {
                    throw new TimestampSyntaxError(format(), ExpectedType.NUMBER, value);
                }
            }

            @Override
            public void writeToSerializer(SdkSchema schema, Instant instant, ShapeSerializer serializer) {
                double value = ((double) instant.toEpochMilli()) / 1000;
                serializer.writeDouble(schema, value);
            }
        },

        DATE_TIME {
            @Override
            public TimestampFormatTrait.Format format() {
                return TimestampFormatTrait.Format.DATE_TIME;
            }

            @Override
            public String writeString(Instant value) {
                return value.toString();
            }

            @Override
            public Instant readFromString(String value, boolean strict) {
                return DateTimeFormatter.ISO_INSTANT.parse(value, Instant::from);
            }
        },

        HTTP_DATE {
            @Override
            public TimestampFormatTrait.Format format() {
                return TimestampFormatTrait.Format.HTTP_DATE;
            }

            @Override
            public String writeString(Instant value) {
                return HTTP_DATE_FORMAT.format(value);
            }

            @Override
            public Instant readFromString(String value, boolean strict) {
                return HTTP_DATE_FORMAT.parse(value, Instant::from);
            }
        };

        private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
            .withZone(ZoneId.of("UTC"))
            .withLocale(Locale.US);

        @Override
        public String toString() {
            return format().toString();
        }

        @Override
        public void writeToSerializer(SdkSchema schema, Instant value, ShapeSerializer serializer) {
            serializer.writeString(schema, writeString(value));
        }

        @Override
        public Instant readFromNumber(Number value) {
            throw new TimestampSyntaxError(format(), ExpectedType.STRING, value);
        }
    }

    /**
     * The type expected when deserializing a timestamp.
     *
     * <p>This can be checked when {@link TimestampSyntaxError} is thrown.
     */
    enum ExpectedType {
        STRING,
        NUMBER
    }

    /**
     * Thrown when a timestamp format cannot be parsed.
     */
    final class TimestampSyntaxError extends SdkSerdeException {

        private final TimestampFormatTrait.Format format;
        private final ExpectedType expectedType;
        private final Object value;

        public TimestampSyntaxError(TimestampFormatTrait.Format format, ExpectedType expectedType, Object value) {
            super(
                "Expected a " + expectedType + " value for a " + format.name() + " timestamp, but found " + value
                    .getClass()
                    .getSimpleName()
            );
            this.format = format;
            this.expectedType = expectedType;
            this.value = value;
        }

        /**
         * Get the timestamp format that failed to parse.
         *
         * @return timestamp format.
         */
        public TimestampFormatTrait.Format format() {
            return format;
        }

        /**
         * Get the expected type the timestamp parser expected.
         *
         * @return the expected type.
         */
        public ExpectedType expectedType() {
            return expectedType;
        }

        /**
         * Get the timestamp value that could not be parsed.
         *
         * @return timestamp value.
         */
        public Object value() {
            return value;
        }
    }
}
