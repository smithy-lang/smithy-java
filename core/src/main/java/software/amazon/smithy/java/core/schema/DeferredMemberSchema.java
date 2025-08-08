/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import software.amazon.smithy.model.traits.Trait;

/**
 * A potentially recursive member schema that targets an unbuilt shape.
 */
final class DeferredMemberSchema extends Schema {

    private final SchemaBuilder target;
    private final TraitMap directTraits;
    private final long requiredByValidationBitmask;

    DeferredMemberSchema(MemberSchemaBuilder builder) {
        super(builder);
        this.target = builder.targetBuilder;
        this.directTraits = builder.directTraits;
        this.requiredByValidationBitmask = builder.requiredByValidationBitmask;
    }

    @Override
    public Schema memberTarget() {
        return target.build();
    }

    @Override
    public Schema member(String memberName) {
        return memberTarget().member(memberName);
    }

    @Override
    public List<Schema> members() {
        return memberTarget().members();
    }

    @Override
    public int requiredMemberCount() {
        return memberTarget().requiredMemberCount();
    }

    @Override
    long requiredByValidationBitmask() {
        // The bitmask applies to the member itself, not its target schema, so this should not be forwarded
        // to memberTarget as the previous accessors do.
        return requiredByValidationBitmask;
    }

    @Override
    long requiredStructureMemberBitfield() {
        return memberTarget().requiredStructureMemberBitfield();
    }

    @Override
    public <T extends Trait> T getDirectTrait(TraitKey<T> trait) {
        return directTraits.get(trait);
    }
}
