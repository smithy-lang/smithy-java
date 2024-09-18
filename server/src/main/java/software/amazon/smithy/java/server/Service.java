/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

public interface Service {

    /**
     * Returns {@link Operation} for a given unqualified operation name.
     * @param operationName Unqualified operation name.
     * @return {@link Operation}
     */
    <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> getOperation(String operationName);

}
