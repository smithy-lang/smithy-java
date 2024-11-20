/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Discovers pagination values from the output shape of an operation call based on a provided paths.
 *
 * <p> Paths are a series of identifiers separated by dots (.) where each identifier represents a member name in a
 * structure.
 */
final class PaginationDiscoveringSerializer extends SpecificShapeSerializer {
    private final String tokenPath;
    private final String itemsPath;

    // Values discovered by the serializer
    private String outputToken = null;
    private int totalItems = 0;
    private boolean itemsFound = false;

    PaginationDiscoveringSerializer(String tokenPath, String itemsPath) {
        this.tokenPath = tokenPath;
        this.itemsPath = itemsPath;
    }

    public String outputToken() {
        return outputToken;
    }

    public int totalItems() {
        return totalItems;
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        struct.serializeMembers(new ValueSerializer(this));
    }

    /**
     * Nested serializer that walks members of a structure to find token.
     *
     * <p>This serializer is context aware, meaning that it tracks which structure it is operating inside.
     * Note: Ignores all shapes that are not structures or strings.
     */
    private static final class ValueSerializer implements ShapeSerializer {
        private final PaginationDiscoveringSerializer root;
        private final String pathPrefix;

        private ValueSerializer(PaginationDiscoveringSerializer root) {
            this.pathPrefix = "";
            this.root = root;
        }

        private ValueSerializer(ValueSerializer parentSerializer, String parentMember) {
            this.root = parentSerializer.root;
            this.pathPrefix = parentSerializer.pathPrefix.isEmpty()
                ? parentMember
                : parentSerializer.pathPrefix + "." + parentMember;
        }

        private boolean memberMatchesPath(Schema memberSchema, String path) {
            if (path == null) {
                return false;
            }
            var memberPath = pathPrefix + '.' + memberSchema.memberName();
            return memberPath.equalsIgnoreCase(path);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            // If the output token and (optional) total number of items have been determined, stop search.
            if (root.outputToken != null && root.itemsPath != null && root.itemsFound) {
                return;
            }

            // Otherwise, keep walking tree for token and item values if they have not been found.
            struct.serializeMembers(new ValueSerializer(this, schema.memberName()));
        }

        @Override
        public void writeString(Schema schema, String value) {
            if (memberMatchesPath(schema, root.tokenPath)) {
                root.outputToken = value;
            }
        }

        @Override
        public <T> void writeList(Schema schema, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
            if (memberMatchesPath(schema, root.itemsPath)) {
                root.totalItems += size;
                root.itemsFound = true;
            }
        }

        @Override
        public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
            // Ignore if we have already found the items member or if this member does not match path.
            if (memberMatchesPath(schema, root.itemsPath)) {
                root.totalItems += size;
                root.itemsFound = true;
            }
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            // Ignore
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            // Ignore
        }

        @Override
        public void writeShort(Schema schema, short value) {
            // Ignore
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            // Ignore
        }

        @Override
        public void writeLong(Schema schema, long value) {
            // Ignore
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            // Ignore
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            // Ignore
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            // Ignore
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            // Ignore
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            // Ignore
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            // Ignore
        }

        @Override
        public void writeDocument(Schema schema, Document value) {
            // Ignore
        }

        @Override
        public void writeNull(Schema schema) {
            // Ignore
        }
    }
}
