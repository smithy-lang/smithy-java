/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.any.Any;
import com.jsoniter.spi.JsonException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.TimestampFormatter;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Resolves the timestamp format to use for a shape.
 */
sealed interface TimestampResolver {
    /**
     * Determine the formatter of a shape.
     *
     * @param schema Shape to resolve.
     * @return resolved formatter.
     */
    TimestampFormatter resolve(SdkSchema schema);

    /**
     * Get the default formatter.
     *
     * @return default formatter.
     */
    TimestampFormatter defaultFormat();

    /**
     * Reusable method to parse timestamps from an iterator using a specific formatter.
     *
     * @param any    JSON any type to read from.
     * @param format Formatter used to parse the timestamp.
     * @return the parsed Instant.
     * @throws SdkSerdeException if the timestamp format or type is invalid.
     */
    static Instant readTimestamp(Any any, TimestampFormatter format) {
        try {
            return switch (any.valueType()) {
                case NUMBER -> format.readFromNumber(any.toDouble());
                case STRING -> format.readFromString(any.toString(), true);
                default -> {
                    throw new SdkSerdeException(
                        "Expected a timestamp, but found " + any.valueType().toString().toLowerCase(Locale.ENGLISH)
                    );
                }
            };
        } catch (JsonException e) {
            throw new SdkSerdeException(e);
        }
    }

    /**
     * Always returns the same default timestamp format and ignores the timestampFormat trait.
     */
    record StaticFormat(TimestampFormatter defaultFormat) implements TimestampResolver {
        @Override
        public TimestampFormatter resolve(SdkSchema schema) {
            return defaultFormat;
        }

        @Override
        public TimestampFormatter defaultFormat() {
            return defaultFormat;
        }

        @Override
        public String toString() {
            return "TimestampResolver{useTimestampFormat=false; default=" + defaultFormat + '}';
        }
    }

    /**
     * Uses the timestampFormat trait if present, otherwise uses a configurable default format.
     */
    final class UseTimestampFormatTrait implements TimestampResolver {
        private final ConcurrentHashMap<SdkSchema, TimestampFormatter> cache = new ConcurrentHashMap<>();
        private final TimestampFormatter defaultFormat;

        UseTimestampFormatTrait(TimestampFormatter defaultFormat) {
            this.defaultFormat = defaultFormat;
        }

        @Override
        public TimestampFormatter defaultFormat() {
            return defaultFormat;
        }

        @Override
        public TimestampFormatter resolve(SdkSchema schema) {
            return cache.computeIfAbsent(schema, s -> {
                var trait = schema.getTrait(TimestampFormatTrait.class);
                return trait != null ? TimestampFormatter.of(trait) : defaultFormat;
            });
        }

        @Override
        public String toString() {
            return "TimestampResolver{useTimestampFormat=true; default=" + defaultFormat + '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (o == null || getClass() != o.getClass()) {
                return false;
            } else {
                return defaultFormat.equals(((UseTimestampFormatTrait) o).defaultFormat);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(defaultFormat);
        }
    }
}
