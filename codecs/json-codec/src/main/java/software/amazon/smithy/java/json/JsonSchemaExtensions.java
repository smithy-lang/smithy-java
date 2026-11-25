/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.schema.MemberLookup;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaExtensionKey;
import software.amazon.smithy.java.core.schema.SchemaExtensionProvider;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.java.core.serde.TimestampFormatter;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DiscriminatedTrait;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.SerializedString;

/**
 * Pre-computes JSON codec data on Schema objects at construction time.
 */
public final class JsonSchemaExtensions implements SchemaExtensionProvider<JsonSchemaExtensions.JsonSchemaExtension> {

    /**
     * Extension key for JSON-specific schema data.
     */
    public static final SchemaExtensionKey<JsonSchemaExtension> KEY = new SchemaExtensionKey<>();

    /**
     * Pre-computed JSON data stored on a Schema.
     *
     * @param discriminated non-null only for union schemas carrying {@code @discriminated}.
     */
    public record JsonSchemaExtension(
            SerializableString jsonFieldName,
            SerializableString memberFieldName,
            MemberLookup jsonMemberLookup,
            TimestampFormatter timestampFormatter,
            DiscriminatedData discriminated) {}

    /**
     * Pre-computed {@code @discriminated} union data for the Jackson backend.
     *
     * @param discriminatorFieldName the discriminator field name read on the wire, e.g. {@code type}.
     * @param serializedFieldName the discriminator field name, pre-encoded for writing.
     * @param serializedValues indexed by memberIndex: the discriminator value (member name) of each member,
     *                         pre-encoded for writing.
     * @param valueLookup resolves the active member from a discriminator value (member name).
     */
    public record DiscriminatedData(
            String discriminatorFieldName,
            SerializableString serializedFieldName,
            SerializableString[] serializedValues,
            MemberLookup valueLookup) {}

    @Override
    public SchemaExtensionKey<JsonSchemaExtension> key() {
        return KEY;
    }

    @Override
    public JsonSchemaExtension provide(Schema schema) {
        if (schema.isMember()) {
            return forMember(schema);
        }
        var type = schema.type();
        if (type == ShapeType.STRUCTURE || type == ShapeType.UNION) {
            return forStruct(schema);
        }
        return forRoot(schema);
    }

    private static JsonSchemaExtension forMember(Schema schema) {
        var jsonNameTrait = schema.getTrait(TraitKey.JSON_NAME_TRAIT);
        String jsonName = jsonNameTrait != null ? jsonNameTrait.getValue() : schema.memberName();

        SerializableString jsonFieldName = new SerializedString(jsonName);
        SerializableString memberFieldName = new SerializedString(schema.memberName());

        TimestampFormatter formatter = null;
        var tsTrait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (tsTrait != null) {
            formatter = TimestampFormatter.match(tsTrait.getFormat());
        }

        return new JsonSchemaExtension(jsonFieldName, memberFieldName, null, formatter, null);
    }

    private static JsonSchemaExtension forRoot(Schema schema) {
        var tsTrait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (tsTrait != null) {
            var formatter = TimestampFormatter.match(tsTrait.getFormat());
            return new JsonSchemaExtension(null, null, null, formatter, null);
        }
        return null;
    }

    private static JsonSchemaExtension forStruct(Schema schema) {
        DiscriminatedData discriminated = null;
        if (schema.type() == ShapeType.UNION) {
            var trait = schema.getTrait(TraitKey.get(DiscriminatedTrait.class));
            if (trait != null) {
                discriminated = buildDiscriminatedData(schema, trait.getValue());
            }
        }
        return new JsonSchemaExtension(null, null, buildMemberLookup(schema.members()), null, discriminated);
    }

    private static DiscriminatedData buildDiscriminatedData(Schema schema, String discriminatorFieldName) {
        List<Schema> members = schema.members();
        SerializableString[] serializedValues = new SerializableString[members.size()];
        for (Schema m : members) {
            // The discriminator value is the member name (see the discriminated-unions design).
            serializedValues[m.memberIndex()] = new SerializedString(m.memberName());
        }
        // The union schema already looks up its members by name, which is the discriminator value.
        return new DiscriminatedData(
                discriminatorFieldName,
                new SerializedString(discriminatorFieldName),
                serializedValues,
                schema);
    }

    private static MemberLookup buildMemberLookup(List<Schema> members) {
        if (members.isEmpty()) {
            return name -> null;
        }
        Map<String, Schema> jsonNameMap = HashMap.newHashMap(members.size());
        for (Schema m : members) {
            var jsonNameTrait = m.getTrait(TraitKey.JSON_NAME_TRAIT);
            jsonNameMap.put(jsonNameTrait != null ? jsonNameTrait.getValue() : m.memberName(), m);
        }
        return jsonNameMap::get;
    }
}
