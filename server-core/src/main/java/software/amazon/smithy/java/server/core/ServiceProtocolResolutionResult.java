/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public record ServiceProtocolResolutionResult(
    Service service,
    Operation<? extends SerializableStruct, ? extends SerializableStruct> operation,
    ServerProtocol protocol
) {
}
