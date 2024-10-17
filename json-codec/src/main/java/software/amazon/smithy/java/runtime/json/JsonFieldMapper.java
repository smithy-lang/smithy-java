/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;

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

        private UseMemberName() {}

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
            var members = jsonNameCache.get(container);

            if (members == null) {
                Map<String, Schema> fresh = new HashMap<>(container.members().size());
                for (Schema m : container.members()) {
                    var jsonName = m.getTrait(TraitKey.JSON_NAME_TRAIT);
                    fresh.put(jsonName != null ? jsonName.getValue() : m.memberName(), m);
                }
                var previous = jsonNameCache.putIfAbsent(container, fresh);
                members = previous == null ? fresh : previous;
            }

            return members.get(field);
        }

        @Override
        public String memberToField(Schema member) {
            var jsonName = member.getTrait(TraitKey.JSON_NAME_TRAIT);
            return jsonName == null ? member.memberName() : jsonName.getValue();
        }

        @Override
        public String toString() {
            return "FieldMapper{useJsonName=true}";
        }
    }
}
