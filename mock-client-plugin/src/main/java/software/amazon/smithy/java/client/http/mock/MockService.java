/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;

/**
 * Package-private MockService.
 *
 * <p>It just throws and does nothing.
 */
final class MockService implements Service {
    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> getOperation(
        String operationName
    ) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
        return List.of();
    }

    @Override
    public Schema schema() {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeRegistry errorRegistry() {
        return TypeRegistry.empty();
    }
}
