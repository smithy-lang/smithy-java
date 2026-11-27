/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DiscriminatedTrait;

/**
 * Pre-computes native JSON codec data for unions carrying the {@code @discriminated} trait.
 *
 * <p>Only union schemas carrying {@code @discriminated} get a non-null extension. Other shapes
 * (including plain unions) return {@code null}.
 *
 * <p>The extension carries both the encode-side pre-encoded discriminator field/value tokens and
 * the decode-side raw discriminator field name plus a {@link SmithyMemberLookup} keyed by the
 * discriminator values (which, per the design, are the union member names). Keeping both sides on
 * one extension means a single {@code getExtension} probe serves both the serializer and the
 * deserializer.
 */
public final class DiscriminatedUnionSchemaExtensions
        implements SchemaExtensionProvider<DiscriminatedUnionSchemaExtensions.DiscriminatedUnionExtension> {

    /**
     * Extension key for discriminated-union JSON codec data.
     */
    public static final SchemaExtensionKey<DiscriminatedUnionExtension> KEY = new SchemaExtensionKey<>();

    /**
     * Pre-computed native JSON data for a {@code @discriminated} union schema.
     *
     * @param discriminatorFieldNameBytes the discriminator field name pre-encoded as a JSON field name
     *                                    prefix, e.g. {@code "type":} (encode side)
     * @param discriminatorValueBytesTable indexed by memberIndex: pre-encoded discriminator value for each
     *                                     member, e.g. {@code "circle"} (encode side)
     * @param discriminatorRawNameBytes the raw UTF-8 bytes of the discriminator field name, e.g. {@code type},
     *                                  for matching the field name during decode (decode side)
     * @param valueLookup member lookup keyed by discriminator value (member name) for resolving the active
     *                    variant during decode (decode side)
     */
    public record DiscriminatedUnionExtension(
            byte[] discriminatorFieldNameBytes,
            byte[][] discriminatorValueBytesTable,
            byte[] discriminatorRawNameBytes,
            SmithyMemberLookup valueLookup) {}

    @Override
    public SchemaExtensionKey<DiscriminatedUnionExtension> key() {
        return KEY;
    }

    @Override
    public DiscriminatedUnionExtension provide(Schema schema) {
        if (schema.isMember()) {
            return schema.memberTarget().getExtension(KEY);
        }
        if (schema.type() != ShapeType.UNION) {
            return null;
        }
        var discriminated = schema.getTrait(TraitKey.get(DiscriminatedTrait.class));
        if (discriminated == null) {
            return null;
        }

        String discriminatorName = discriminated.getValue();
        byte[] discriminatorFieldNameBytes = JsonWriteUtils.encodeFieldNameToken(discriminatorName);
        byte[] discriminatorRawNameBytes = discriminatorName.getBytes(StandardCharsets.UTF_8);

        List<Schema> members = schema.members();
        byte[][] discriminatorValueBytesTable = new byte[members.size()][];
        for (Schema m : members) {
            discriminatorValueBytesTable[m.memberIndex()] = JsonWriteUtils.encodeQuotedValueToken(m.memberName());
        }

        // The discriminator value is the member name, so a member-name-keyed lookup resolves
        // the active variant directly from the discriminator value bytes.
        SmithyMemberLookup valueLookup = new SmithyMemberLookup(members, false);

        return new DiscriminatedUnionExtension(
                discriminatorFieldNameBytes,
                discriminatorValueBytesTable,
                discriminatorRawNameBytes,
                valueLookup);
    }
}
