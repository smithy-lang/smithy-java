/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events.model;

import software.amazon.smithy.java.core.error.ErrorFault;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Base-level exception for the service.
 *
 * <p>Some exceptions do not extend from this class, including synthetic, implicit, and shared
 * exception types.
 */
@SmithyGenerated
public abstract class EventStreamingTestServiceException extends ModeledException {
    protected EventStreamingTestServiceException(
            Schema schema,
            String message,
            Throwable cause,
            ErrorFault errorType,
            Boolean captureStackTrace,
            boolean deserialized
    ) {
        super(schema, message, cause, errorType, captureStackTrace, deserialized);
    }
}
