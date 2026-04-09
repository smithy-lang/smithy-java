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
     */
    public record JsonSchemaExtension(
            SerializableString jsonFieldName,
            SerializableString memberFieldName,
            MemberLookup jsonMemberLookup,
            TimestampFormatter timestampFormatter) {}

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

        return new JsonSchemaExtension(jsonFieldName, memberFieldName, null, formatter);
    }

    private static JsonSchemaExtension forRoot(Schema schema) {
        var tsTrait = schema.getTrait(TraitKey.TIMESTAMP_FORMAT_TRAIT);
        if (tsTrait != null) {
            var formatter = TimestampFormatter.match(tsTrait.getFormat());
            return new JsonSchemaExtension(null, null, null, formatter);
        }
        return null;
    }

    private static JsonSchemaExtension forStruct(Schema schema) {
        return new JsonSchemaExtension(null, null, buildMemberLookup(schema.members()), null);
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
