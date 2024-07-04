/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.EnumTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * A fully resolved, non-recursive schema that may contain members.
 */
final class RootSchema extends Schema {

    private final Map<String, Schema> members;
    private final List<Schema> memberList;
    private final Set<String> stringEnumValues;
    private final Set<Integer> intEnumValues;

    RootSchema(ShapeType type, ShapeId id, Map<Class<? extends Trait>, Trait> traits) {
        this(type, id, traits, Collections.emptyList(), detectEnumTraitValues(type, traits), Collections.emptySet());
    }

    // String shapes might just have an enum trait, so find those and use them as enum values.
    private static Set<String> detectEnumTraitValues(ShapeType type, Map<Class<? extends Trait>, Trait> traits) {
        if (type == ShapeType.STRING) {
            var enumTrait = (EnumTrait) traits.get(EnumTrait.class);
            if (enumTrait != null) {
                return new HashSet<>(enumTrait.getEnumDefinitionValues());
            }
        }
        return Collections.emptySet();
    }

    RootSchema(
        ShapeType type,
        ShapeId id,
        Map<Class<? extends Trait>, Trait> traits,
        List<MemberSchemaBuilder> memberBuilders,
        Set<String> stringEnumValues,
        Set<Integer> intEnumValues
    ) {
        super(type, id, traits, memberBuilders, stringEnumValues);
        this.stringEnumValues = Collections.unmodifiableSet(stringEnumValues);
        this.intEnumValues = Collections.unmodifiableSet(intEnumValues);

        if (memberBuilders.isEmpty()) {
            memberList = Collections.emptyList();
            members = Collections.emptyMap();
        } else {
            memberList = new ArrayList<>(memberBuilders.size());
            for (var builder : memberBuilders) {
                memberList.add(builder.build());
            }
            members = SchemaBuilder.createMembers(memberList);
        }
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
    public Set<String> stringEnumValues() {
        return stringEnumValues;
    }
}
