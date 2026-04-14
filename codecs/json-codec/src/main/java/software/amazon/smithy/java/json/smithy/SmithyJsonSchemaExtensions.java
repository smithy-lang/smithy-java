/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Pre-computes native JSON codec data on Schema objects.
 *
 * <p>For member schemas: pre-encoded UTF-8 byte arrays for field name prefixes ({@code "fieldName":}).
 * For struct/union schemas: {@link SmithyMemberLookup} instances for hash-based field matching.
 */
public final class SmithyJsonSchemaExtensions
        implements SchemaExtensionProvider<SmithyJsonSchemaExtensions.NativeJsonExtension> {

    /**
     * Extension key for native JSON codec data.
     */
    public static final SchemaExtensionKey<NativeJsonExtension> KEY = new SchemaExtensionKey<>();

    /**
     * Pre-computed native JSON data stored on a Schema.
     *
     * @param jsonNameBytes       Field name as {@code "jsonName":} bytes (null for non-members)
     * @param memberNameBytes     Field name as {@code "memberName":} bytes (null for non-members)
     * @param jsonNameLookup      Hash-based member lookup using jsonName (null for non-structs)
     * @param memberNameLookup    Hash-based member lookup using member name (null for non-structs)
     * @param jsonFieldNameTable  Indexed by memberIndex: pre-computed jsonName bytes per member (null for non-structs)
     * @param memberFieldNameTable Indexed by memberIndex: pre-computed memberName bytes per member (null for non-structs)
     */
    public record NativeJsonExtension(
            byte[] jsonNameBytes,
            byte[] memberNameBytes,
            SmithyMemberLookup jsonNameLookup,
            SmithyMemberLookup memberNameLookup,
            byte[][] jsonFieldNameTable,
            byte[][] memberFieldNameTable) {}

    @Override
    public SchemaExtensionKey<NativeJsonExtension> key() {
        return KEY;
    }

    @Override
    public NativeJsonExtension provide(Schema schema) {
        if (schema.isMember()) {
            return forMember(schema);
        }
        var type = schema.type();
        if (type == ShapeType.STRUCTURE || type == ShapeType.UNION) {
            return forStruct(schema);
        }
        return null;
    }

    private static NativeJsonExtension forMember(Schema schema) {
        var jsonNameTrait = schema.getTrait(TraitKey.JSON_NAME_TRAIT);
        String jsonName = jsonNameTrait != null ? jsonNameTrait.getValue() : schema.memberName();

        byte[] jsonNameBytes = JsonWriteUtils.precomputeFieldNameBytes(jsonName);
        byte[] memberNameBytes = JsonWriteUtils.precomputeFieldNameBytes(schema.memberName());

        return new NativeJsonExtension(jsonNameBytes, memberNameBytes, null, null, null, null);
    }

    private static NativeJsonExtension forStruct(Schema schema) {
        List<Schema> members = schema.members();
        if (members.isEmpty()) {
            return new NativeJsonExtension(null, null, null, null, null, null);
        }

        SmithyMemberLookup jsonNameLookup = new SmithyMemberLookup(members, true);
        SmithyMemberLookup memberNameLookup = new SmithyMemberLookup(members, false);

        // Build indexed field name tables for O(1) lookup by memberIndex during serialization.
        // This avoids per-field VarHandle acquire reads on member schema extensions.
        int maxIndex = 0;
        for (Schema m : members) {
            maxIndex = Math.max(maxIndex, m.memberIndex());
        }
        byte[][] jsonFieldNameTable = new byte[maxIndex + 1][];
        byte[][] memberFieldNameTable = new byte[maxIndex + 1][];
        for (Schema m : members) {
            int idx = m.memberIndex();
            var jsonNameTrait = m.getTrait(TraitKey.JSON_NAME_TRAIT);
            String jsonName = jsonNameTrait != null ? jsonNameTrait.getValue() : m.memberName();
            jsonFieldNameTable[idx] = JsonWriteUtils.precomputeFieldNameBytes(jsonName);
            memberFieldNameTable[idx] = JsonWriteUtils.precomputeFieldNameBytes(m.memberName());
        }

        return new NativeJsonExtension(
                null,
                null,
                jsonNameLookup,
                memberNameLookup,
                jsonFieldNameTable,
                memberFieldNameTable);
    }
}
