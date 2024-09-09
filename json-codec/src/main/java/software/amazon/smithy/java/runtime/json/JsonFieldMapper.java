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
public enum JsonFieldMapper {

    MEMBER_NAME {
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
    },

    JSON_NAME {
        private final Map<Schema, Map<String, Schema>> jsonNameCache = new ConcurrentHashMap<>();

        @Override
        public Schema fieldToMember(Schema container, String field) {
            var members = jsonNameCache.get(container);

            if (members == null) {
                members = new HashMap<>(container.members().size());
                for (Schema m : container.members()) {
                    var jsonName = m.getTrait(JsonNameTrait.class);
                    if (jsonName != null) {
                        members.put(jsonName.getValue(), m);
                    } else {
                        members.put(m.memberName(), m);
                    }
                }
            }

            return members.get(field);
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
    };

    /**
     * Determines the schema of a member inside a container based on a JSON field name.
     *
     * @param container Container that contains members.
     * @param field     JSON object field name.
     * @return the resolved member schema or null if not found.
     */
    public abstract Schema fieldToMember(Schema container, String field);

    /**
     * Converts a member schema a JSON object field name.
     *
     * @param member Member to convert to a field.
     * @return the resolved object field name.
     */
    public abstract String memberToField(Schema member);
}
