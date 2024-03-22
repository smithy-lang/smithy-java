/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.interceptor;

import java.util.List;
import software.amazon.smithy.java.runtime.core.context.Context;
import software.amazon.smithy.java.runtime.core.shapes.SerializableShape;

public interface ClientInterceptor {

    ClientInterceptor NOOP = new ClientInterceptor() {
    };

    static ClientInterceptor chain(List<ClientInterceptor> interceptors) {
        return new ClientInterceptorChain(interceptors);
    }

    default void readBeforeExecution(Context context) {}

    default <I extends SerializableShape> I modifyInputBeforeSerialization(I input, Context context) {
        return input;
    }

    default void readBeforeSerialization(Context context) {}

    default void readAfterSerialization(Context context) {}

    default <T> T modifyRequestBeforeRetryLoop(Context context, T request) {
        return request;
    }

    default void readBeforeAttempt(Context context) {}

    default <T> T modifyRequestBeforeSigning(Context context, T request) {
        return request;
    }

    default void readBeforeSigning(Context context) {}

    default void readAfterSigning(Context context) {}

    default <T> T modifyRequestBeforeTransmit(Context context, T request) {
        return request;
    }

    default void readBeforeTransmit(Context context) {}

    default void readAfterTransmit(Context context) {}

    default <T> T modifyResponseBeforeDeserialization(Context context, T response) {
        return response;
    }

    default void readResponseBeforeDeserialization(Context context) {}

    default void readAfterDeserialization(Context context) {}

    default <O extends SerializableShape> O modifyOutputBeforeAttemptCompletion(O output, Context context) {
        return output;
    }

    default void readAfterAttempt(Context context) {}

    default <O extends SerializableShape> O modifyOutputBeforeCompletion(O output, Context context) {
        return output;
    }

    default void readAfterExecution(Context context) {}
}
