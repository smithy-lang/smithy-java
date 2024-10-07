/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A possibly recursive schema that may contain members, some of which aren't built yet.
 */
final class DeferredRootSchema extends Schema {

    private final List<MemberSchemaBuilder> memberBuilders;
    private final Set<String> stringEnumValues;
    private final Set<Integer> intEnumValues;
    private volatile ResolvedMembers resolvedMembers;

    DeferredRootSchema(
        ShapeType type,
        ShapeId id,
        TraitMap traits,
        List<MemberSchemaBuilder> memberBuilders,
        Set<String> stringEnumValues,
        Set<Integer> intEnumValues
    ) {
        super(type, id, traits, memberBuilders, stringEnumValues);
        this.stringEnumValues = Collections.unmodifiableSet(stringEnumValues);
        this.intEnumValues = Collections.unmodifiableSet(intEnumValues);
        this.memberBuilders = memberBuilders;
    }

    private record ResolvedMembers(Map<String, Schema> members, List<Schema> memberList) {}

    private void resolve() {
        if (resolvedMembers == null) {
            List<Schema> memberList = new ArrayList<>(memberBuilders.size());
            for (var builder : memberBuilders) {
                memberList.add(builder.build());
            }
            resolvedMembers = new ResolvedMembers(SchemaBuilder.createMembers(memberList), memberList);
        }
    }

    @Override
    public List<Schema> members() {
        resolve();
        return resolvedMembers.memberList;
    }

    @Override
    public Schema member(String memberName) {
        resolve();
        return resolvedMembers.members.get(memberName);
    }

    @Override
    public Set<Integer> intEnumValues() {
        return intEnumValues;
    }

    @Override
    public Set<String> stringEnumValues() {
        return stringEnumValues;
    }
}
