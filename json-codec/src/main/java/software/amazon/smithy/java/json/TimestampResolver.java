/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.TimestampFormatter;

/**
 * Resolves the timestamp format to use for a shape.
 */
public sealed interface TimestampResolver {
    /**
     * Determine the formatter of a shape.
     *
     * @param schema Shape to resolve.
     * @return resolved formatter.
     */
    TimestampFormatter resolve(Schema schema);

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
     * @throws SerializationException if the timestamp format or type is invalid.
     */
    static Instant readTimestamp(Object any, TimestampFormatter format) {
        if (any instanceof Number n) {
            return format.readFromNumber(n.doubleValue());
        }
        if (any instanceof String s) {
            return format.readFromString(s, true);
        }
        throw new SerializationException(
                "Expected a timestamp, but found " + any.getClass().getSimpleName());
    }

    /**
     * Always returns the same default timestamp format and ignores the timestampFormat trait.
     */
    record StaticFormat(TimestampFormatter defaultFormat) implements TimestampResolver {
        @Override
        public TimestampFormatter resolve(Schema schema) {
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
        private final ConcurrentHashMap<Schema, TimestampFormatter> cache = new ConcurrentHashMap<>();
        private final TimestampFormatter defaultFormat;

        UseTimestampFormatTrait(TimestampFormatter defaultFormat) {
            this.defaultFormat = defaultFormat;
        }

        @Override
        public TimestampFormatter defaultFormat() {
            return defaultFormat;
        }

        @Override
        public TimestampFormatter resolve(Schema schema) {
            var result = cache.get(schema);
            if (result == null) {
                var trait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
                var fresh = trait != null ? TimestampFormatter.of(trait) : defaultFormat;
                var previous = cache.putIfAbsent(schema, fresh);
                result = previous == null ? fresh : previous;
            }
            return result;
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
