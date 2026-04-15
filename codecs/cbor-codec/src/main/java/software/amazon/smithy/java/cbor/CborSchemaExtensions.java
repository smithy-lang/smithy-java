/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Pre-computes CBOR codec data on Schema objects.
 *
 * <p>For member schemas: pre-encoded CBOR text string header + member name bytes.
 * For struct/union schemas: {@link CborMemberLookup} instances for hash-based field matching
 * and field name tables for O(1) lookup by memberIndex during serialization.
 */
public final class CborSchemaExtensions
        implements SchemaExtensionProvider<CborSchemaExtensions.NativeCborExtension> {

    /**
     * Extension key for CBOR codec data.
     */
    public static final SchemaExtensionKey<NativeCborExtension> KEY = new SchemaExtensionKey<>();

    /**
     * Pre-computed CBOR data stored on a Schema.
     *
     * @param memberNameBytes  CBOR text string header + name bytes (null for non-members)
     * @param memberLookup     Hash-based member lookup (null for non-structs)
     * @param fieldNameTable   Indexed by memberIndex: pre-computed name bytes per member (null for non-structs)
     */
    public record NativeCborExtension(
            byte[] memberNameBytes,
            CborMemberLookup memberLookup,
            byte[][] fieldNameTable) {}

    @Override
    public SchemaExtensionKey<NativeCborExtension> key() {
        return KEY;
    }

    @Override
    public NativeCborExtension provide(Schema schema) {
        if (schema.isMember()) {
            return forMember(schema);
        }
        var type = schema.type();
        if (type == ShapeType.STRUCTURE || type == ShapeType.UNION) {
            return forStruct(schema);
        }
        return null;
    }

    private static NativeCborExtension forMember(Schema schema) {
        byte[] memberNameBytes = CborSerializer.encodeMemberName(schema.memberName());
        return new NativeCborExtension(memberNameBytes, null, null);
    }

    private static NativeCborExtension forStruct(Schema schema) {
        List<Schema> members = schema.members();
        if (members.isEmpty()) {
            return new NativeCborExtension(null, null, null);
        }

        CborMemberLookup memberLookup = new CborMemberLookup(members);

        // Build indexed field name table for O(1) lookup by memberIndex during serialization.
        int maxIndex = 0;
        for (Schema m : members) {
            maxIndex = Math.max(maxIndex, m.memberIndex());
        }
        byte[][] fieldNameTable = new byte[maxIndex + 1][];
        for (Schema m : members) {
            int idx = m.memberIndex();
            fieldNameTable[idx] = CborSerializer.encodeMemberName(m.memberName());
        }

        return new NativeCborExtension(null, memberLookup, fieldNameTable);
    }
}
