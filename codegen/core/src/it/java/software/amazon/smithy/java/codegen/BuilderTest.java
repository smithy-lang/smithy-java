/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.codegen.test.model.ListMembersInput;
import io.smithy.codegen.test.model.MapMembersInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


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
