/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.RequiredTrait;

public class MemberSortTest {

    @Test
    public void sortsMembersByWireCategoryThenIdx() {
        var schema = Schema.structureBuilder(ShapeId.from("test#Sorting"))
                .putMember("string", PreludeSchemas.STRING, new IdxTrait(1))
                .putMember("map",
                        Schema.mapBuilder(ShapeId.from("test#Map"))
                                .putMember("key", PreludeSchemas.STRING)
                                .putMember("value", PreludeSchemas.STRING)
                                .build(),
                        new IdxTrait(2))
                .putMember("i", PreludeSchemas.INTEGER, new IdxTrait(5))
                .putMember("l", PreludeSchemas.LONG, new IdxTrait(6))
                .putMember("d", PreludeSchemas.DOUBLE, new IdxTrait(8))
                .putMember("f", PreludeSchemas.FLOAT, new IdxTrait(9))
                .putMember("bool", PreludeSchemas.BOOLEAN, new IdxTrait(14))
                .putMember("time", PreludeSchemas.TIMESTAMP, new IdxTrait(22))
                .build();

        var names = schema.members().stream().map(Schema::memberName).toList();

        // Varints by idx, then four-byte, then eight-byte by idx, then length-delimited by idx.
        assertThat(names).containsExactly("i", "l", "bool", "f", "d", "time", "string", "map");
        for (int i = 0; i < names.size(); i++) {
            assertThat(schema.member(names.get(i)).memberIndex()).isEqualTo(i);
        }
    }

    @Test
    public void idxBreaksTiesWithinCategoryOnly() {
        var schema = Schema.structureBuilder(ShapeId.from("test#IdxTies"))
                .putMember("b", PreludeSchemas.STRING, new IdxTrait(2))
                .putMember("a", PreludeSchemas.STRING, new IdxTrait(1))
                .putMember("noIdx", PreludeSchemas.STRING)
                .build();

        var names = schema.members().stream().map(Schema::memberName).toList();

        // Members with idx sort before members without; ties keep insertion order.
        assertThat(names).containsExactly("a", "b", "noIdx");
    }

    @Test
    public void unionMembersSortByWireCategory() {
        var schema = Schema.unionBuilder(ShapeId.from("test#U"))
                .putMember("s", PreludeSchemas.STRING)
                .putMember("i", PreludeSchemas.INTEGER)
                .build();

        assertThat(schema.members().get(0).memberName()).isEqualTo("i");
        assertThat(schema.members().get(1).memberName()).isEqualTo("s");
    }

    @Test
    public void requiredValidationDoesNotDependOnMemberOrder() {
        // The required member ("name", a string) sorts AFTER the optional int member, so its memberIndex is
        // not 0, but its validation bit index is 0 because it's the first (only) required member.
        var schema = Schema.structureBuilder(ShapeId.from("test#Req"))
                .putMember("age", PreludeSchemas.INTEGER)
                .putMember("name", PreludeSchemas.STRING, new RequiredTrait())
                .build();

        var name = schema.member("name");
        assertThat(name.memberIndex()).isEqualTo(1);

        var tracker = PresenceTracker.of(schema);
        assertThat(tracker.allSet()).isFalse();
        assertThat(tracker.getMissingMembers()).containsExactly("name");
        tracker.setMember(name);
        assertThat(tracker.allSet()).isTrue();

        // Bitfield validation: bit 0 = first required member in sorted order.
        PresenceTracker.validateRequiredMembers(schema, 0x1L);
        assertThatThrownBy(() -> PresenceTracker.validateRequiredMembers(schema, 0x0L))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("name");
    }

    @Test
    public void validatesMultipleRequiredMembersAcrossCategories() {
        var schema = Schema.structureBuilder(ShapeId.from("test#Req2"))
                .putMember("s", PreludeSchemas.STRING, new RequiredTrait())
                .putMember("i", PreludeSchemas.INTEGER, new RequiredTrait())
                .putMember("opt", PreludeSchemas.STRING)
                .build();

        // Sorted order: i (varint), s (list), opt (list). Validation bits: i=0x1, s=0x2.
        var missingBoth = List.of("i", "s");
        assertThatThrownBy(() -> PresenceTracker.validateRequiredMembers(schema, 0x0L))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining(missingBoth.toString());
        assertThatThrownBy(() -> PresenceTracker.validateRequiredMembers(schema, 0x1L))
                .isInstanceOf(SerializationException.class)
                .hasMessageContaining("s")
                .hasMessageNotContaining("i,");
        PresenceTracker.validateRequiredMembers(schema, 0x3L);
    }
}
