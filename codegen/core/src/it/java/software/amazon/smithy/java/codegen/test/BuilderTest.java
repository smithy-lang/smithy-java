/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.codegen.test.model.ListMembersInput;
import software.amazon.smithy.java.codegen.test.model.MapMembersInput;


public class BuilderTest {

    @Test
    public void testToBuilderList() {
        ListMembersInput original = ListMembersInput.builder()
            .requiredList(List.of("A"))
            .build();
        var copy = original.toBuilder().optionalList(List.of("1")).build();
        assertThat(copy)
            .returns(original.requiredList(), ListMembersInput::requiredList)
            .returns(List.of("1"), ListMembersInput::optionalList);
    }

    @Test
    public void testToBuilderMap() {
        MapMembersInput original = MapMembersInput.builder()
            .requiredMap(Map.of("A", "B"))
            .build();
        var copy = original.toBuilder().optionalMap(Map.of("1", "2")).build();
        assertThat(copy)
            .returns(original.requiredMap(), MapMembersInput::requiredMap)
            .returns(Map.of("1", "2"), MapMembersInput::optionalMap);
    }
}
