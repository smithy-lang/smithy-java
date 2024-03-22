/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

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
     * Create a formatter from a timestamp format trait.
     *
     * @param trait Trait to create the format from.
     * @return Returns the created formatter.
     */
    static TimestampFormatter of(TimestampFormatTrait trait) {
        return of(trait.getValue());
    }

    /**
     * Create a formatter from a string name.
     *
     * @param name Name to create from.
     * @return Returns the created formatter.
     */
    static TimestampFormatter of(String name) {
        return switch (name) {
            case "date-time" -> Prelude.DATE_TIME;
            case "epoch-seconds" -> Prelude.EPOCH_SECONDS;
            case "http-date" -> Prelude.HTTP_DATE;
            default -> throw new IllegalStateException("Unknown timestamp format: " + name);
        };
    }

    /**
     * Get the identifier of the format (e.g., "date-time").
     *
     * @return Return the identifier.
     */
    String getIdentifier();

    /**
     * Format the given Instant to a String.
     *
     *
     * <p>Even if the instant normally serializes into a number, this method must return that number as a string.
     *
     * @param value Value to format.
     * @return Returns the formatted string.
     */
    String formatToString(Instant value);

    /**
     * Parse the timestamp from a string.
     *
     * <p>This method must be able to parse the output of {@link #formatToString(Instant)}.
     *
     * @param value Value to parse.
     * @param strict Set to true to throw if the string value comes from a serialization format that should
     *               serialize this format as a number.
     * @return Returns the created Instant.
     */
    Instant parseFromString(String value, boolean strict);

    /**
     * Create the timestamp from a number, if possible.
     *
     * @param value Value to convert into a timestamp.
     * @return Returns the created Instant.
     * @throws IllegalArgumentException if the format does not support numeric values.
     */
    Instant createFromNumber(Number value);

    /**
     * Writes the timestamp value to given serializer using the underlying data format for the timestamp.
     *
     * <p>This method must not attempt to write a timestamp as that will cause infinite recursion. Instead, it
     * should serialize the timestamp as a string or number to the given serializer.
     *
     * @param schema     Schema of the timestamp.
     * @param value      Timestamp value to serialize.
     * @param serializer Where to serialize the data.
     */
    void serializeToUnderlyingFormat(SdkSchema schema, Instant value, ShapeSerializer serializer);

    /**
     * Defines the underlying encoding type of a timestamp value.
     */
    enum FormatValueType {
        /** The value is a string. */
        STRING,

        /** The value should be a number. */
        NUMBER
    }

    /**
     * Formats built into the Smithy prelude.
     */
    enum Prelude implements TimestampFormatter {
        EPOCH_SECONDS {
            @Override
            public String getIdentifier() {
                return "epoch-seconds";
            }

            @Override
            public String formatToString(Instant value) {
                return String.format("%.3f", ((double) value.toEpochMilli()) / 1000);
            }

            @Override
            public Instant parseFromString(String value, boolean strict) {
                if (strict) {
                    throw new IllegalArgumentException(
                            "Expected a numeric value for a " + getIdentifier() + " timestamp, but found a string");
                }
                return Instant.ofEpochMilli((long) (Double.parseDouble(value) * 1000));
            }

            @Override
            public Instant createFromNumber(Number value) {
                if (value instanceof Integer i) {
                    return Instant.ofEpochMilli(i * 1000);
                } else if (value instanceof Long l) {
                    return Instant.ofEpochMilli(l * 1000);
                } else if (value instanceof Float f) {
                    return Instant.ofEpochMilli((long) (f * 1000f));
                } else if (value instanceof Double f) {
                    return Instant.ofEpochMilli((long) (f * 1000f));
                } else {
                    throw new IllegalArgumentException("Expected numeric value for epoch-seconds to be an "
                            + "integer, long, float, or double, but found " + value.getClass().getName());
                }
            }

            @Override
            public void serializeToUnderlyingFormat(SdkSchema schema, Instant instant, ShapeSerializer serializer) {
                double value = ((double) instant.toEpochMilli()) / 1000;
                serializer.writeDouble(schema, value);
            }
        },

        DATE_TIME {
            @Override
            public String getIdentifier() {
                return "date-time";
            }

            @Override
            public String formatToString(Instant value) {
                return value.toString();
            }

            @Override
            public Instant parseFromString(String value, boolean strict) {
                return DateTimeFormatter.ISO_INSTANT.parse(value, Instant::from);
            }
        },

        HTTP_DATE {
            @Override
            public String getIdentifier() {
                return "http-date";
            }

            @Override
            public String formatToString(Instant value) {
                return HTTP_DATE_FORMAT.format(value);
            }

            @Override
            public Instant parseFromString(String value, boolean strict) {
                return HTTP_DATE_FORMAT.parse(value, Instant::from);
            }
        };

        private static final DateTimeFormatter HTTP_DATE_FORMAT = DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
                .withZone(ZoneId.of("UTC"))
                .withLocale(Locale.US);

        @Override
        public String toString() {
            return getIdentifier();
        }

        @Override
        public void serializeToUnderlyingFormat(SdkSchema schema, Instant value, ShapeSerializer serializer) {
            serializer.writeString(schema, formatToString(value));
        }

        @Override
        public Instant createFromNumber(Number value) {
            throw new IllegalStateException(
                    "Expected a string value for a " + getIdentifier() + " timestamp, but found a number");
        }
    }
}
