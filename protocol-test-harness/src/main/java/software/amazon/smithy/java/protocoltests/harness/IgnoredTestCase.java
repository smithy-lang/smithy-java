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

record IgnoredTestCase(String testId) implements TestTemplateInvocationContext {

    @Override
    public String getDisplayName(int invocationIndex) {
        return testId;
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of((ExecutionCondition) context -> ConditionEvaluationResult.disabled(""));
    }

}
