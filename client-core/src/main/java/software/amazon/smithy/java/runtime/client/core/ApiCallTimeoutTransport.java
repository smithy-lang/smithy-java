/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * A {@link ClientTransport} that wraps another transport to time out a request if it takes longer than
 * {@link CallContext#API_CALL_TIMEOUT}.
 *
 * <p>This transport calls the downstream transport using a virtual thread and enforces a timeout while waiting on
 * the tread.
 *
 * <p>This wrapper is added by default in generated clients.
 */
public final class ApiCallTimeoutTransport implements ClientTransport {

    private final ClientTransport delegate;

    public ApiCallTimeoutTransport(ClientTransport delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public <I extends SerializableShape, O extends SerializableShape> CompletableFuture<O> send(ClientCall<I, O> call) {
        // TODO: How to handle API_CALL_TIMEOUT with async. For now making this noop.
        return delegate.send(call);
//        var timeout = call.context().get(CallContext.API_CALL_TIMEOUT);
//
//        if (timeout == null) {
//            return delegate.send(call);
//        }
//
//        // Call the actual service in a virtual thread to support total-call timeout.
//        try {
//            Future<O> result = call.executor().submit(() -> delegate.send(call));
//            try {
//                return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
//            } catch (TimeoutException e) {
//                result.cancel(true);
//                throw e;
//            }
//        } catch (InterruptedException | TimeoutException | ExecutionException e) {
//            if (e.getCause() != null) {
//                String message = "Error calling " + call.operation().schema().id().getName() + ": "
//                    + e.getCause().getMessage();
//                throw new SdkException(message, e.getCause());
//            } else {
//                throw new RuntimeException(e);
//            }
//        }
    }
}
