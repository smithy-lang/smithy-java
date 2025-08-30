/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

/**
 * Initial event types, either a request or a response.
 */
public enum InitialEventType {
    INITIAL_REQUEST("initial-request"),
    INITIAL_RESPONSE("initial-response");

    private final String value;

    InitialEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
