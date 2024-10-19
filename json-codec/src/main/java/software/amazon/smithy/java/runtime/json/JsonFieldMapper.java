/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.core.schema.MemberLookup;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.TraitKey;

/**
 * Provides a mapping to and from members and JSON field names.
 */
public sealed interface JsonFieldMapper {
    /**
     * Returns a member container used to resolve serialized field names into members within a schema.
     *
     * @param container Container that contains members.
     * @return the member container that can be used to get members by name.
     */
    MemberLookup fieldToMember(Schema container);

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
        public MemberLookup fieldToMember(Schema container) {
            return container;
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

        private final Map<Schema, MemberLookup> jsonNameCache = new ConcurrentHashMap<>();

        @Override
        public MemberLookup fieldToMember(Schema container) {
            return jsonNameCache.computeIfAbsent(container, c -> {
                Map<String, Schema> map = new HashMap<>(c.members().size());
                for (Schema m : c.members()) {
                    var jsonName = m.getTrait(TraitKey.JSON_NAME_TRAIT);
                    map.put(jsonName != null ? jsonName.getValue() : m.memberName(), m);
                }
                return map::get;
            });
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
