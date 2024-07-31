/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public sealed interface Job permits DefaultJob {

    Request request();

    Response response();

    Context context();

    boolean isCompleted();

    void complete();

    Throwable getFailure();

    void setFailure(Throwable failure);

    Operation<? extends SerializableStruct, ? extends SerializableStruct> operation();

    ServerProtocol chosenProtocol();
}
