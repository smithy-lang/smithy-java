/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.document;

/**
 * Thrown when a document is unable to find or parse the discriminator of the document into a shape ID.
 */
public class DiscriminatorException extends RuntimeException {

    public DiscriminatorException(String message) {
        super(message);
    }

    public DiscriminatorException(String message, Throwable source) {
        super(message, source);
    }

    public DiscriminatorException(Throwable source) {
        super(source);
    }
}
