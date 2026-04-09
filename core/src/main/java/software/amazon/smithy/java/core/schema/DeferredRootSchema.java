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
import java.util.function.Supplier;
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
    // Written before the volatile resolvedMembers for re-entrant access from
    // initExtensions() -> provide(this) -> schema.members() on the same thread.
    private List<Schema> resolvingMembers;
    private volatile ResolvedMembers resolvedMembers;

    DeferredRootSchema(
            ShapeType type,
            ShapeId id,
            TraitMap traits,
            List<MemberSchemaBuilder> memberBuilders,
            Set<String> stringEnumValues,
            Set<Integer> intEnumValues,
            Supplier<ShapeBuilder<?>> builderSupplier,
            SchemaBuilder schemaBuilder,
            Class<?> shapeClass
    ) {
        super(type, id, traits, memberBuilders, stringEnumValues, builderSupplier, shapeClass);
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
            if (resolvingMembers != null) {
                // Re-entrant call from initExtensions() on the same thread.
                return;
            }
            List<Schema> memberList = new ArrayList<>(memberBuilders.size());
            for (var builder : memberBuilders) {
                memberList.add(builder.build());
            }
            // Store for re-entrant access: initExtensions() -> provide(this) ->
            // schema.members() re-enters resolveInternal() on the same thread.
            this.resolvingMembers = memberList;

            int requiredMemberCount = SchemaBuilder.computeRequiredMemberCount(this.type(), memberBuilders);
            long requiredStructureMemberBitfield = SchemaBuilder.computeRequiredBitField(
                    type(),
                    requiredMemberCount,
                    memberBuilders,
                    m -> m.requiredByValidationBitmask);

            // Initialize extensions BEFORE the volatile write. The subsequent
            // volatile write to resolvedMembers acts as a release fence, ensuring
            // the extensions array is visible to any thread that reads resolvedMembers.
            initExtensions();

            // Volatile write is LAST: publishes both ResolvedMembers and extensions.
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
        var rm = resolvedMembers;
        // During re-entrant init on the same thread, resolvedMembers is still null.
        return rm != null ? rm.memberList : resolvingMembers;
    }

    @Override
    public Schema member(String memberName) {
        resolveInternal();
        var rm = resolvedMembers;
        if (rm != null) {
            return rm.members.get(memberName);
        }
        // Re-entrant path (same thread only): linear scan.
        for (var m : resolvingMembers) {
            if (m.memberName().equals(memberName)) {
                return m;
            }
        }
        return null;
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
