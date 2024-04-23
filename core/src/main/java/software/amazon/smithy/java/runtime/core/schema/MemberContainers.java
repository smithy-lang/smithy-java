/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Holder for Map implementations of List and Map shapes to provide the most appropriate Map for a schema.
 */
final class MemberContainers {

    private MemberContainers() {}

    static Map<String, SdkSchema> of(ShapeType type, List<SdkSchema> members, Map<String, SdkSchema> targetMembers) {
        if (members == null || members.isEmpty()) {
            // Use the members of the target shape directly rather than needing to do a recursive resolution.
            return targetMembers != null ? targetMembers : Map.of();
        } else if (type == ShapeType.LIST) {
            return new MemberContainers.ListMembers(members);
        } else if (type == ShapeType.MAP) {
            return MemberContainers.MapMembers.of(members);
        } else if (members.size() == 1) {
            // Special case for shapes with a single member.
            return Map.of(members.getFirst().memberName(), members.getFirst());
        } else {
            Map<String, SdkSchema> result = new LinkedHashMap<>(members.size());
            for (SdkSchema member : members) {
                result.put(member.memberName(), member);
            }
            return result;
        }
    }

    /**
     * Validates and stores list members that always have a single "member" member.
     */
    private static final class ListMembers extends AbstractMap<String, SdkSchema> {

        private final SdkSchema member;
        private final Set<Entry<String, SdkSchema>> entries;

        ListMembers(List<SdkSchema> members) {
            if (members.size() != 1) {
                throw new IllegalArgumentException("List shapes require exactly one member");
            }

            this.member = members.getFirst();
            if (!member.memberName().equals("member")) {
                throw new IllegalArgumentException("List shapes require a member named 'member'. Found " + member);
            }

            this.entries = Set.of(new SimpleImmutableEntry<>("member", member));
        }

        @Override
        public boolean containsKey(Object key) {
            return "member".equals(key);
        }

        @Override
        public SdkSchema get(Object key) {
            return "member".equals(key) ? member : null;
        }

        @Override
        public Set<Entry<String, SdkSchema>> entrySet() {
            return entries;
        }
    }

    /**
     * Validates and stores map members that always have a "key" and "value" member, in that order.
     */
    private static final class MapMembers extends AbstractMap<String, SdkSchema> {

        private final SdkSchema key;
        private final SdkSchema value;
        private final Set<Entry<String, SdkSchema>> entries;

        private MapMembers(SdkSchema key, SdkSchema value) {
            this.key = key;
            this.value = value;
            this.entries = Set.of(new SimpleImmutableEntry<>("key", key), new SimpleImmutableEntry<>("value", value));
        }

        static MapMembers of(List<SdkSchema> members) {
            if (members.size() != 2) {
                throw new IllegalArgumentException("Maps require exactly two members. Found: " + members);
            } else if (!members.getFirst().memberName().equals("key")) {
                throw new IllegalArgumentException("Maps require a key as the first member. Found: " + members);
            } else if (!members.getLast().memberName().equals("value")) {
                throw new IllegalArgumentException("Maps require a value as the second member. Found: " + members);
            } else {
                return new MapMembers(members.getFirst(), members.getLast());
            }
        }

        @Override
        public boolean containsKey(Object key) {
            return "key".equals(key) || "value".equals(key);
        }

        @Override
        public SdkSchema get(Object key) {
            if ("key".equals(key)) {
                return this.key;
            } else if ("value".equals(key)) {
                return value;
            } else {
                return null;
            }
        }

        @Override
        public Set<Entry<String, SdkSchema>> entrySet() {
            return entries;
        }
    }
}
