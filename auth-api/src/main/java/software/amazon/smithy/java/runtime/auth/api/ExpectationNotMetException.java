/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;


/**
 * Thrown by {@link AuthProperties} methods that expect a property to exist.
 */
public class ExpectationNotMetException extends RuntimeException {
    public ExpectationNotMetException(String message) {
        super(message);
    }
}
