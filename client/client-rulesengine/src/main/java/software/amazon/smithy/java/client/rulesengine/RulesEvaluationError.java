/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

/**
 * An error encountered while running the rules engine.
 */
public class RulesEvaluationError extends RuntimeException {
    public RulesEvaluationError(String message) {
        super(message);
    }

    public RulesEvaluationError(String message, Throwable cause) {
        super(message, cause);
    }
}
