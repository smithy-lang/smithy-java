/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.SpecificShapeDeserializer;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class HttpHeaderListDeserializer extends SpecificShapeDeserializer {

    private final List<String> values;

    HttpHeaderListDeserializer(Schema schema, List<String> values) {
        List<String> actualValues = new ArrayList<>();

        var listMember = schema.memberTarget().listMember();
        switch (listMember.type()) {
            case STRING, ENUM, INT_ENUM, BOOLEAN,
                    BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, BIG_INTEGER, BIG_DECIMAL -> {
                splitString(values, actualValues);
            }
            case TIMESTAMP -> {
                var format = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
                if (format == null || format.getFormat() == TimestampFormatTrait.Format.DATE_TIME) {
                    // Special handling is needed to split multiple timestamps that use DATE_TIME.
                    for (var v : values) {
                        splitHttpDates(v, actualValues);
                    }
                } else {
                    splitString(values, actualValues);
                }
            }
            default -> throw new IllegalStateException("Unsupported header list member: " + schema);
        }

        this.values = actualValues;
    }

    private static void splitString(List<String> values, List<String> accumulator) {
        outer: for (var v : values) {
            boolean hasComma = false;
            // Do a single pass over the string to check if quotes need to be processed or commas.
            for (var i = 0; i < v.length(); i++) {
                var c = v.charAt(i);
                if (c == ',') {
                    // We need to keep scanning when a comma is found in case a quote is found later.
                    hasComma = true;
                } else if (c == '"') {
                    // No need to keep scanning when a quote is found: process it and go to the next value.
                    parseHeader(v, accumulator);
                    continue outer;
                }
            }
            // No quotes at this point, but only split and trim based on comma if a comma was found.
            if (hasComma) {
                for (var split : v.split(",")) {
                    accumulator.add(split.trim());
                }
            } else {
                accumulator.add(v);
            }
        }
    }

    // Parse CSV headers that can contain quoted values.
    private static void parseHeader(String input, List<String> accumulator) {
        StringBuilder currentEntry = new StringBuilder();
        boolean inQuotes = false;
        boolean escaping = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaping) {
                // If the previous character was a backslash, add the current character verbatim.
                currentEntry.append(c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
                currentEntry.append(c);
            } else if (c == ',' && !inQuotes) {
                // If we encounter a comma outside quotes, finalize the current entry.
                accumulator.add(currentEntry.toString().trim());
                currentEntry.setLength(0);
            } else {
                // Append the character to the current entry.
                currentEntry.append(c);
            }
        }

        // Add the last entry if it's not empty.
        if (!currentEntry.isEmpty()) {
            accumulator.add(currentEntry.toString().trim());
        }
    }

    // HTTP dates contain commas. Split every other comma.
    // "Mon, 16 Dec 2019 23:48:18 GMT, Mon, 16 Dec 2019 23:48:18 GMT"
    // Becomes: ["Mon, 16 Dec 2019 23:48:18 GMT", "Mon, 16 Dec 2019 23:48:18 GMT"]
    private static void splitHttpDates(String input, List<String> accumulator) {
        StringBuilder currentPart = new StringBuilder();
        int commaCount = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == ',') {
                commaCount++;
                if (commaCount % 2 == 0) {
                    accumulator.add(currentPart.toString().trim());
                    currentPart.setLength(0);
                    continue;
                }
            }
            currentPart.append(c);
        }

        if (!currentPart.isEmpty()) {
            accumulator.add(currentPart.toString().trim());
        }
    }

    @Override
    protected RuntimeException throwForInvalidState(Schema schema) {
        throw new UnsupportedOperationException("List header deserialization not supported for " + schema);
    }

    @Override
    public <T> void readList(Schema schema, T state, ListMemberConsumer<T> listMemberConsumer) {
        for (String value : values) {
            listMemberConsumer.accept(state, new HttpHeaderDeserializer(value));
        }
    }

    @Override
    public boolean isNull() {
        return values == null;
    }

    @Override
    public int containerSize() {
        return values == null ? 0 : values.size();
    }
}
