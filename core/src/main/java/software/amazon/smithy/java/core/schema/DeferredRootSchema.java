/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

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

    final List<MemberSchemaBuilder> memberBuilders;
    private final SchemaBuilder schemaBuilder;
    final Set<String> stringEnumValues;
    final Set<Integer> intEnumValues;
    private volatile ResolvedMembers resolvedMembers;

    DeferredRootSchema(
            ShapeType type,
            ShapeId id,
            TraitMap traits,
            List<MemberSchemaBuilder> memberBuilders,
            Set<String> stringEnumValues,
            Set<Integer> intEnumValues,
            SchemaBuilder schemaBuilder
    ) {
        super(type, id, traits, memberBuilders, stringEnumValues);
        this.stringEnumValues = Collections.unmodifiableSet(stringEnumValues);
        this.intEnumValues = Collections.unmodifiableSet(intEnumValues);
        this.memberBuilders = memberBuilders;
        this.schemaBuilder = schemaBuilder;
    }

    record ResolvedMembers(
            Map<String, Schema> members,
            List<Schema> memberList,
            int requiredMemberCount,
            long requiredStructureMemberBitfield) {}

    @Override
    public Schema resolve() {
        resolveInternal();
        var resolved = new ResolvedRootSchema(this);
        schemaBuilder.resolve(resolved);
        return resolved;
    }

    private void resolveInternal() {
        if (resolvedMembers == null) {
            List<Schema> memberList = new ArrayList<>(memberBuilders.size());
            for (var builder : memberBuilders) {
                memberList.add(builder.build());
            }
            int requiredMemberCount = SchemaBuilder.computeRequiredMemberCount(this.type(), memberBuilders);
            long requiredStructureMemberBitfield = SchemaBuilder.computeRequiredBitField(
                    type(),
                    requiredMemberCount,
                    memberBuilders,
                    m -> m.requiredByValidationBitmask);
            this.resolvedMembers = new ResolvedMembers(SchemaBuilder.createMembers(memberList),
                    memberList,
                    requiredMemberCount,
                    requiredStructureMemberBitfield);

        }
    }

    ResolvedMembers resolvedMembers() {
        resolveInternal();
        return resolvedMembers;
    }

    @Override
    public List<Schema> members() {
        resolveInternal();
        return resolvedMembers.memberList;
    }

    @Override
    public Schema member(String memberName) {
        resolveInternal();
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

    @Override
    int requiredMemberCount() {
        resolveInternal();
        return resolvedMembers.requiredMemberCount;
    }

    @Override
    long requiredByValidationBitmask() {
        return 0;
    }

    @Override
    long requiredStructureMemberBitfield() {
        resolveInternal();
        return resolvedMembers.requiredStructureMemberBitfield;
    }
}
