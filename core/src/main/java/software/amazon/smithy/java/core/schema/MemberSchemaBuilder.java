/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.Collections;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.Trait;

/**
 * A builder used to create member schemas.
 */
final class MemberSchemaBuilder {

    final ShapeType type;
    final ShapeId id;
    final TraitMap traits;
    final TraitMap directTraits;

    final Schema target;
    final SchemaBuilder targetBuilder;

    final boolean isRequiredByValidation;
    final SchemaBuilder.ValidationState validationState;

    int memberIndex;
    long requiredByValidationBitmask;

    MemberSchemaBuilder(ShapeId id, Schema target, Trait[] traits) {
        this(id, target, null, traits);
    }

    MemberSchemaBuilder(ShapeId id, SchemaBuilder targetBuilder, Trait[] traits) {
        this(id, null, targetBuilder, traits);
    }

    private MemberSchemaBuilder(ShapeId id, Schema target, SchemaBuilder targetBuilder, Trait[] traits) {
        validateTarget(id, target != null && target.isMember());

        this.target = target;
        this.targetBuilder = targetBuilder;
        this.id = id;
        this.type = target == null ? targetBuilder.type : target.type();
        this.directTraits = TraitMap.create(traits);

        // Try to optimally combine traits.
        var targetTraits = target != null ? target.traits : targetBuilder.traits;
        this.traits = targetTraits.withMemberTraits(directTraits);

        this.isRequiredByValidation = computeIsRequired();
        this.validationState = SchemaBuilder.ValidationState.of(
                type,
                this.traits,
                target == null ? Collections.emptySet() : target.stringEnumValues());
    }

    private void validateTarget(ShapeId targetId, boolean isMember) {
        if (isMember) {
            throw new IllegalArgumentException("Cannot target a member: " + targetId);
        } else if (targetId.getMember().isEmpty()) {
            throw new IllegalArgumentException("Provided ID must have a member name");
        }
    }

    private boolean computeIsRequired() {
        if (!traits.contains(TraitKey.REQUIRED_TRAIT)) {
            return false;
        }
        var defaultValue = traits.get(TraitKey.DEFAULT_TRAIT);
        return defaultValue == null || defaultValue.toNode().isNullNode();
    }

    Schema build() {
        return targetBuilder != null ? new DeferredMemberSchema(this) : new MemberSchema(this);
    }

    // Setting the member index has to be deferred until a shape is built because members are sorted by their
    // wire category when the containing shape is built. This method is called when a Schema is built.
    void setMemberIndex(int index) {
        this.memberIndex = index;
    }

    // The validation index is this member's sequential position among the containing structure's
    // required-by-validation members, in memberIndex order. It determines the member's bit in the required
    // member bitfield and is independent of memberIndex, so member sorting places no constraints on where
    // required members land. Generated builders assign bits by the same rule.
    void setValidationIndex(int index) {
        this.requiredByValidationBitmask = index < 64 ? 1L << index : 0L;
    }
}
