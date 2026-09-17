/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import software.amazon.smithy.utils.SmithyInternalApi;

/** Raised when strict runtime codegen cannot produce a codec. */
@SmithyInternalApi
public final class RuntimeCodegenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    RuntimeCodegenException(String message, Throwable cause) {
        super(message, cause);
    }
}
