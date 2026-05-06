/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core;

/**
 * Thrown when incompatible versions of Smithy Java modules are detected on the classpath.
 */
public final class IncompatibleVersionException extends RuntimeException {
    IncompatibleVersionException(String message) {
        super(message);
    }
}
