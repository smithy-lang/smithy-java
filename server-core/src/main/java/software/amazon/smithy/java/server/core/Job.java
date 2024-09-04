/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;

public sealed interface Job permits DefaultJob {

    Request request();

    Response response();

    boolean isCompleted();

    void complete();

    Throwable getFailure();

    default boolean isFailure() {
        return getFailure() != null;
    }

    void setFailure(Throwable failure);

    Operation<? extends SerializableStruct, ? extends SerializableStruct> operation();

    ServerProtocol chosenProtocol();

    default boolean isHttpJob() {
        return false;
    }

    /**
     * Returns a HttpJob if the current Job is actually a HttpJob. Users are expected to invoke isHttpJob prior to this to confirm.
     *
     * @return HttpJob
     * @throws ClassCastException if this is not a HttpJob.
     */
    default HttpJob asHttpJob() {
        return (HttpJob) this;
    }

}
