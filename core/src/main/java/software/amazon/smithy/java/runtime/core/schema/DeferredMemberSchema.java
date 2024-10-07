/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.List;
import software.amazon.smithy.model.traits.Trait;

/**
 * A potentially recursive member schema that targets an unbuilt shape.
 */
final class DeferredMemberSchema extends Schema {

    private final SchemaBuilder target;
    private final TraitMap directTraits;

    DeferredMemberSchema(MemberSchemaBuilder builder) {
        super(builder);
        this.target = builder.targetBuilder;
        this.directTraits = builder.directTraits;
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
    public <T extends Trait> T getDirectTrait(Class<T> trait) {
        return directTraits.get(trait);
    }
}
