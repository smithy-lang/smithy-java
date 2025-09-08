/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Resolved Schema which was initially deferred.
 */
final class ResolvedRootSchema extends Schema {

    private final Map<String, Schema> members;
    private final List<Schema> memberList;
    private final int requiredMemberCount;
    private final long requiredStructureMemberBitfield;
    private final Set<Integer> intEnumValues;

    ResolvedRootSchema(DeferredRootSchema deferredRootSchema) {
        super(deferredRootSchema.type(),
                deferredRootSchema.id(),
                deferredRootSchema.traits,
                deferredRootSchema.memberBuilders,
                deferredRootSchema.stringEnumValues,
                deferredRootSchema.shapeBuilder);
        var resolvedMembers = deferredRootSchema.resolvedMembers();
        this.memberList = resolvedMembers.memberList();
        this.requiredMemberCount = resolvedMembers.requiredMemberCount();
        this.requiredStructureMemberBitfield = resolvedMembers.requiredStructureMemberBitfield();
        this.members = resolvedMembers.members();
        this.intEnumValues = deferredRootSchema.intEnumValues;

    }

    @Override
    public List<Schema> members() {
        return memberList;
    }

    @Override
    public Schema member(String memberName) {
        return members.get(memberName);
    }

    @Override
    public Set<Integer> intEnumValues() {
        return intEnumValues;
    }

    @Override
    int requiredMemberCount() {
        return requiredMemberCount;
    }

    @Override
    long requiredByValidationBitmask() {
        return 0;
    }

    @Override
    long requiredStructureMemberBitfield() {
        return requiredStructureMemberBitfield;
    }

}
