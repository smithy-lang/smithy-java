/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.model.traits.JsonNameTrait;

/**
 * Provides a mapping to and from members and JSON field names.
 */
public sealed interface JsonFieldMapper {
    /**
     * Determines the schema of a member inside a container based on a JSON field name.
     *
     * @param container Container that contains members.
     * @param field     JSON object field name.
     * @return the resolved member schema or null if not found.
     */
    Schema fieldToMember(Schema container, String field);

    /**
     * Converts a member schema a JSON object field name.
     *
     * @param member Member to convert to a field.
     * @return the resolved object field name.
     */
    String memberToField(Schema member);

    /**
     * Uses the member name and ignores the jsonName trait.
     */
    final class UseMemberName implements JsonFieldMapper {
        static final UseMemberName INSTANCE = new UseMemberName();

        @Override
        public Schema fieldToMember(Schema container, String field) {
            return container.member(field);
        }

        @Override
        public String memberToField(Schema member) {
            return member.memberName();
        }

        @Override
        public String toString() {
            return "FieldMapper{useJsonName=false}";
        }
    }

    /**
     * Uses the jsonName trait if present, otherwise falls back to the member name.
     */
    final class UseJsonNameTrait implements JsonFieldMapper {

        private final Map<Schema, Map<String, Schema>> jsonNameCache = new ConcurrentHashMap<>();

        @Override
        public Schema fieldToMember(Schema container, String field) {
            return jsonNameCache.computeIfAbsent(container, schema -> {
                Map<String, Schema> map = new HashMap<>(schema.members().size());
                for (Schema m : schema.members()) {
                    var jsonName = m.getTrait(JsonNameTrait.class);
                    if (jsonName != null) {
                        map.put(jsonName.getValue(), m);
                    } else {
                        map.put(m.memberName(), m);
                    }
                }
                return map;
            }).get(field);
        }

        @Override
        public String memberToField(Schema member) {
            var jsonName = member.getTrait(JsonNameTrait.class);
            return jsonName == null ? member.memberName() : jsonName.getValue();
        }

        @Override
        public String toString() {
            return "FieldMapper{useJsonName=true}";
        }
    }
}
