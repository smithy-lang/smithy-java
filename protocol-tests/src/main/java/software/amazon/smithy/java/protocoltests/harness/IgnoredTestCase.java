/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.util.List;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import software.amazon.smithy.protocoltests.traits.HttpMessageTestCase;

record IgnoredTestCase(HttpMessageTestCase testCase) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return testCase.getId();
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of((ExecutionCondition) context -> ConditionEvaluationResult.disabled(""));
    }

}
