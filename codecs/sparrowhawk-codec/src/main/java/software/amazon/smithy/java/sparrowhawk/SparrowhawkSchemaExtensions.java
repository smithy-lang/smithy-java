/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.sparrowhawk;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.SchemaUtils;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Precomputes Sparrowhawk layout data on {@link Schema} objects.
 *
 * <p>Because the runtime sorts structure and union members by wire category and then by the
 * {@code smithy.protocols#idx} trait, memberIndex order equals on-wire order: each type-section occupies a
 * contiguous memberIndex range, and a member's in-section field index (kIdx) is its memberIndex minus the
 * section's first memberIndex. This layout is byte-compatible with the reference implementation, which
 * ranks members by idx within each section.
 *
 * <p>Member schemas resolve to their target's layout, so serializers and deserializers can fetch the
 * layout with a single extension lookup regardless of whether they hold the member or the target schema.
 */
@SmithyInternalApi
public final class SparrowhawkSchemaExtensions implements SchemaExtensionProvider<SparrowhawkSchemaExtensions.Layout> {

    public static final SchemaExtensionKey<Layout> KEY = new SchemaExtensionKey<>();

    /**
     * Precomputed layout for a structure/union (member tables) or list/map (element info) schema.
     */
    public static final class Layout {
        /** Number of members of a structure/union. */
        final int memberCount;
        /**
         * Indexed by memberIndex: {@code (group << 8) | (section << 6) | bitInGroup}. Null for
         * non-structure schemas.
         */
        final int[] memberInfo;
        /** First memberIndex of each type-section (indexed by section code). */
        final int[] sectionStart;
        /** Number of members in each type-section (indexed by section code). */
        final int[] sectionCount;
        /** reverse[section][kIdx] -> member schema, null entries for unmapped fields. */
        final Schema[][] reverse;
        /** For LIST: element wire tag. For MAP: value-list wire tag. */
        final int elementTag;
        /** Whether a LIST/MAP is sparse (nullable elements/values encoded as wrappers). */
        final boolean sparse;
        /**
         * For STRUCTURE/UNION: bit i set when the member with memberIndex i (i < 64) targets a leaf type
         * (scalar, string, blob, big number). A struct whose presence bits show exactly one present leaf
         * member serializes fully forward with no prefix reservation.
         */
        final long leafMembers;
        /**
         * Indexed by memberIndex (structs with <= 63 members only): shift of the member's group slice
         * within the presence bitfield.
         */
        final int[] sliceShift;
        /** Indexed by memberIndex (structs with <= 63 members only): mask of the member's group slice. */
        final long[] sliceMask;

        private Layout(
                int memberCount,
                int[] memberInfo,
                int[] sectionStart,
                int[] sectionCount,
                Schema[][] reverse,
                int elementTag,
                boolean sparse,
                long leafMembers,
                int[] sliceShift,
                long[] sliceMask
        ) {
            this.memberCount = memberCount;
            this.memberInfo = memberInfo;
            this.sectionStart = sectionStart;
            this.sectionCount = sectionCount;
            this.reverse = reverse;
            this.elementTag = elementTag;
            this.sparse = sparse;
            this.leafMembers = leafMembers;
            this.sliceShift = sliceShift;
            this.sliceMask = sliceMask;
        }
    }

    @Override
    public SchemaExtensionKey<Layout> key() {
        return KEY;
    }

    @Override
    public Layout provide(Schema schema) {
        if (schema.isMember()) {
            // Members share the target's layout; getExtension caches the resolved value per schema.
            return schema.memberTarget().getExtension(KEY);
        }
        return switch (schema.type()) {
            case STRUCTURE, UNION -> forStruct(schema);
            case LIST, SET -> collection(schema.listMember(), schema);
            case MAP -> collection(schema.mapValueMember(), schema);
            default -> null;
        };
    }

    /** Maps a shape type to its type-section code. Identical grouping to {@link SchemaUtils#memberSortRank}. */
    static int sectionOf(ShapeType type) {
        return switch (SchemaUtils.memberSortRank(type)) {
            case 0 -> Sparrowhawk.T_VARINT;
            case 1 -> Sparrowhawk.T_FOUR;
            case 2 -> Sparrowhawk.T_EIGHT;
            default -> Sparrowhawk.T_LIST;
        };
    }

    /** Leaf types encode as pure bytes with no nested structure (scalars, strings, blobs, big numbers). */
    private static boolean isLeaf(ShapeType type) {
        return switch (type) {
            case STRUCTURE, UNION, LIST, SET, MAP, DOCUMENT -> false;
            default -> true;
        };
    }

    private static int listElementTag(Schema member) {
        return switch (sectionOf(member.memberTarget().type())) {
            case Sparrowhawk.T_VARINT -> Sparrowhawk.LIST_VARINTS;
            case Sparrowhawk.T_FOUR -> Sparrowhawk.LIST_FOUR;
            case Sparrowhawk.T_EIGHT -> Sparrowhawk.LIST_EIGHT;
            default -> Sparrowhawk.LIST_LEN_DELIMITED;
        };
    }

    private static Layout collection(Schema valueMember, Schema schema) {
        boolean sparse = schema.hasTrait(TraitKey.SPARSE_TRAIT);
        // Sparse elements are wrapped in byte-lists, so the on-wire element type is length-delimited.
        int tag = sparse ? Sparrowhawk.LIST_LEN_DELIMITED : listElementTag(valueMember);
        return new Layout(0, null, null, null, null, tag, sparse, 0, null, null);
    }

    private static Layout forStruct(Schema schema) {
        var members = schema.members();
        int n = members.size();
        int[] info = new int[n];
        int[] start = new int[4];
        int[] count = new int[4];

        for (Schema m : members) {
            count[sectionOf(m.memberTarget().type())]++;
        }
        // Sections occupy contiguous memberIndex ranges in emission order: VARINT, FOUR, EIGHT, LIST.
        start[Sparrowhawk.T_VARINT] = 0;
        start[Sparrowhawk.T_FOUR] = count[Sparrowhawk.T_VARINT];
        start[Sparrowhawk.T_EIGHT] = start[Sparrowhawk.T_FOUR] + count[Sparrowhawk.T_FOUR];
        start[Sparrowhawk.T_LIST] = start[Sparrowhawk.T_EIGHT] + count[Sparrowhawk.T_EIGHT];

        Schema[][] reverse = new Schema[4][];
        for (int s = 0; s < 4; s++) {
            reverse[s] = count[s] == 0 ? null : new Schema[count[s]];
        }
        long leafMembers = 0;
        for (Schema m : members) {
            int mi = m.memberIndex();
            if (mi < 64 && isLeaf(m.memberTarget().type())) {
                leafMembers |= 1L << mi;
            }
            int section = sectionOf(m.memberTarget().type());
            int kIdx = mi - start[section];
            if (kIdx < 0 || kIdx >= count[section]) {
                // Cannot happen for schemas built through the core sort; guard against foreign schemas.
                throw new IllegalStateException(
                        "Sparrowhawk layout requires members grouped by wire category: " + schema.id());
            }
            info[mi] = (kIdx / Sparrowhawk.FIELDS_PER_GROUP) << 8
                    | section << 6
                    | (kIdx % Sparrowhawk.FIELDS_PER_GROUP);
            reverse[section][kIdx] = m;
        }

        int[] sliceShift = null;
        long[] sliceMask = null;
        if (n > 0 && n <= 63) {
            sliceShift = new int[n];
            sliceMask = new long[n];
            for (int mi = 0; mi < n; mi++) {
                int in = info[mi];
                int section = (in >>> 6) & 3;
                int group = in >>> 8;
                int groupStart = start[section] + group * Sparrowhawk.FIELDS_PER_GROUP;
                int width = Math.min(
                        count[section] - group * Sparrowhawk.FIELDS_PER_GROUP,
                        Sparrowhawk.FIELDS_PER_GROUP);
                sliceShift[mi] = groupStart;
                sliceMask[mi] = (1L << width) - 1;
            }
        }
        return new Layout(n,
                info,
                start,
                count,
                reverse,
                0,
                false,
                leafMembers,
                sliceShift,
                sliceMask);
    }
}
