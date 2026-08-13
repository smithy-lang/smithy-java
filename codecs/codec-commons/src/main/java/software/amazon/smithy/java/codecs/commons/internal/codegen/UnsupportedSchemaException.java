/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

public final class UnsupportedSchemaException extends RuntimeException {
    public UnsupportedSchemaException(String message) {
        super(message);
    }
}
