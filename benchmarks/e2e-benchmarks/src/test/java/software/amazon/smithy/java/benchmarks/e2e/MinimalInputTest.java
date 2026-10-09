/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

class MinimalInputTest {

    private static final String NAMESPACE = "com.amazonaws.sdk.benchmark";

    @Test
    void populatesOnlyLabelsAndRequiredMembers() {
        var model = BenchmarkCases.model();
        var getObject = model.expectShape(ShapeId.fromParts(NAMESPACE, "GetObject"), OperationShape.class);

        var params = MinimalInput.forOperation(model, getObject);

        assertThat(params.getStringMap().keySet()).containsExactlyInAnyOrder("Bucket", "Key");
        assertThat(params.expectStringMember("Bucket").getValue()).isNotBlank();
    }

    @Test
    void coversEveryRequiredMemberOfEveryOperationInput() {
        var model = BenchmarkCases.model();
        for (OperationShape operation : model.getOperationShapes()) {
            if (!operation.getId().getNamespace().equals(NAMESPACE)) {
                continue;
            }
            var input = model.expectShape(operation.getInputShape(), StructureShape.class);
            List<String> expected = new ArrayList<>();
            for (var member : input.members()) {
                if (member.hasTrait(RequiredTrait.class) || member.hasTrait(HttpLabelTrait.class)) {
                    expected.add(member.getMemberName());
                }
            }

            var params = MinimalInput.forOperation(model, operation);

            assertThat(params.getStringMap().keySet())
                    .as("required members of %s", operation.getId())
                    .containsExactlyInAnyOrderElementsOf(expected);
        }
    }
}
