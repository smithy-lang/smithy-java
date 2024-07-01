/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public class MemberContainersTest {

    private final ShapeId id = ShapeId.from("smithy.example#Foo");

    @Test
    public void listShapesRequireOneMember() {
        var members = List.of(
            Schema.memberBuilder("member", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.LIST, members, Map.of());
        });
    }

    @Test
    public void listShapesRequireOneMemberNamedMember() {
        var members = List.of(Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.LIST, members, Map.of());
        });
    }

    @Test
    public void listContainerReturnsValues() {
        var members = List.of(Schema.memberBuilder("member", () -> PreludeSchemas.STRING).id(id).build());
        var listMembers = MemberContainers.of(ShapeType.LIST, members, Map.of());

        assertThat(listMembers.values(), hasSize(1));
        assertThat(listMembers.entrySet(), hasSize(1));
        assertThat(listMembers.containsKey("member"), is(true));
        assertThat(listMembers.get("member"), is(members.get(0)));
        assertThat(listMembers.get("foo"), nullValue());
    }

    @Test
    public void mapShapesRequireTwoMembers() {
        var members = List.of(Schema.memberBuilder("key", () -> PreludeSchemas.STRING).id(id).build());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.MAP, members, Map.of());
        });
    }

    @Test
    public void mapShapesRequireKeyAndValue() {
        var members = List.of(
            Schema.memberBuilder("key", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.MAP, members, Map.of());
        });
    }

    @Test
    public void mapShapesRequireKeyAndValueOtherOrder() {
        var members = List.of(
            Schema.memberBuilder("value", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.MAP, members, Map.of());
        });
    }

    @Test
    public void mapShapesRequireKeyAndValueBothWrong() {
        var members = List.of(
            Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("bar", () -> PreludeSchemas.STRING).id(id).build()
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MemberContainers.of(ShapeType.MAP, members, Map.of());
        });
    }

    @Test
    public void mapContainerReturnsKeysAndValues() {
        var members = List.of(
            Schema.memberBuilder("key", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("value", () -> PreludeSchemas.STRING).id(id).build()
        );

        var mapMembers = MemberContainers.of(ShapeType.MAP, members, Map.of());

        assertThat(mapMembers.containsKey("key"), is(true));
        assertThat(mapMembers.containsKey("value"), is(true));
        assertThat(mapMembers.containsKey("foo"), is(false));
        assertThat(mapMembers.get("key"), is(members.get(0)));
        assertThat(mapMembers.get("value"), is(members.get(members.size() - 1)));
        assertThat(mapMembers.get("foo"), nullValue());
        assertThat(mapMembers.entrySet(), hasSize(2));
    }

    @Test
    public void returnsSingleItemContainer() {
        var members = List.of(Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build());

        var memberMap = MemberContainers.of(ShapeType.STRUCTURE, members, Map.of());

        assertThat(memberMap.containsKey("foo"), is(true));
        assertThat(memberMap.get("foo"), is(members.get(0)));
    }

    @Test
    public void createsOtherKindsOfMemberMaps() {
        var members = List.of(
            Schema.memberBuilder("foo", () -> PreludeSchemas.STRING).id(id).build(),
            Schema.memberBuilder("bar", () -> PreludeSchemas.STRING).id(id).build()
        );

        var memberMap = MemberContainers.of(ShapeType.STRUCTURE, members, Map.of());

        assertThat(memberMap.values(), hasSize(2));
        assertThat(memberMap.containsKey("foo"), is(true));
        assertThat(memberMap.get("foo"), is(members.get(0)));
        assertThat(memberMap.containsKey("bar"), is(true));
        assertThat(memberMap.get("bar"), is(members.get(members.size() - 1)));
    }
}
