/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.Objects;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public abstract sealed class DefaultJob implements Job permits HttpJob {

    private final Operation<? extends SerializableStruct, ? extends SerializableStruct> operation;
    private final ServerProtocol protocol;
    private volatile Throwable failure;

    protected DefaultJob(
        Operation<? extends SerializableStruct, ? extends SerializableStruct> operation,
        ServerProtocol protocol
    ) {
        this.operation = Objects.requireNonNull(operation, "Operation must not be null");
        this.protocol = Objects.requireNonNull(protocol, "Protocol must not be null");
    }

    @Override
    public final boolean isCompleted() {
        return false;
    }

    @Override
    public final void complete() {

    }

    @Override
    public final Throwable getFailure() {
        return failure;
    }

    @Override
    public final void setFailure(Throwable failure) {
        this.failure = failure;
    }

    @Override
    public final Operation<? extends SerializableStruct, ? extends SerializableStruct> operation() {
        return operation;
    }

    @Override
    public final ServerProtocol chosenProtocol() {
        return protocol;
    }
}
