/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.codegen.test.model.ListsInput;
import io.smithy.codegen.test.model.MapsInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;


public class BuilderTest {

    @Test
    public void testToBuilderList() {

        ListsInput original = ListsInput.builder()
            .requiredList(List.of("A"))
            .build();
        var copy = original.toBuilder().optionalList(List.of("1")).build();
        assertThat(copy)
            .returns(original.requiredList(), ListsInput::requiredList)
            .returns(List.of("1"), ListsInput::optionalList);
    }

    @Test
    public void testToBuilderMap() {
        MapsInput original = MapsInput.builder()
            .requiredMap(Map.of("A", "B"))
            .build();
        var copy = original.toBuilder().optionalMap(Map.of("1", "2")).build();
        assertThat(copy)
            .returns(original.requiredMap(), MapsInput::requiredMap)
            .returns(Map.of("1", "2"), MapsInput::optionalMap);
    }
}
