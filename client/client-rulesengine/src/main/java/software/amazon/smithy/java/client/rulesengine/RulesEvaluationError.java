/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.rulesengine;

/**
 * An error encountered while running the rules engine.
 */
public class RulesEvaluationError extends RuntimeException {

    private final int position;

    public RulesEvaluationError(String message) {
        this(message, -1);
    }

    public RulesEvaluationError(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public RulesEvaluationError(String message, int position) {
        super(createMessage(message, position));
        this.position = position;
    }

    public RulesEvaluationError(String message, int position, Throwable cause) {
        super(createMessage(message, position), cause);
        this.position = position;
    }

    private static String createMessage(String message, int position) {
        return message + (position == -1 ? "" : " (position " + position + ')');
    }

    public int getPosition() {
        return position;
    }
}
