/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public final class DefaultJob implements Job {
    @Override
    public Request request() {
        return null;
    }

    @Override
    public Response response() {
        return null;
    }

    @Override
    public Context context() {
        return null;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public void complete() {

    }

    @Override
    public Throwable getFailure() {
        return null;
    }

    @Override
    public void setFailure(Throwable failure) {

    }

    @Override
    public Operation<? extends SerializableStruct, ? extends SerializableStruct> operation() {
        return null;
    }

    @Override
    public ServerProtocol chosenProtocol() {
        return null;
    }
}
