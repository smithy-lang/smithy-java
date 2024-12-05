/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.mock;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.server.core.ServerProtocol;

/**
 * A thread-safe mutable queue of {@link MockedResult} values to return from a {@link MockPlugin}.
 */
public final class MockQueue {

    private final Queue<MockedResult> queue = new ConcurrentLinkedQueue<>();

    /**
     * Dequeue a result from the queue.
     *
     * @return the result or null if no results are available.
     */
    public MockedResult poll() {
        return queue.poll();
    }

    /**
     * Get the remaining number of results in the queue.
     *
     * @return the remaining result count.
     */
    public int remaining() {
        return queue.size();
    }

    /**
     * Clear the result queue.
     */
    public MockQueue clear() {
        queue.clear();
        return this;
    }

    /**
     * Enqueue a mock HTTP response.
     *
     * @param response Response to enqueue.
     */
    public MockQueue enqueue(HttpResponse response) {
        queue.offer(new MockedResult.Response(response));
        return this;
    }

    /**
     * Enqueue a mocked output shape, relying on on-demand server protocol resolution in the mock transport.
     *
     * <p>If a corresponding protocol can't be found, an exception is thrown at that point.
     *
     * <p>Exceptions that implement {@link SerializableStruct} can be enqueued here too if you want the entire
     * serialization process to occur. Other exceptions should be given explicitly to {@link #enqueueError}.
     *
     * @param output the output to enqueue.
     */
    public MockQueue enqueue(SerializableStruct output) {
        enqueue(null, output);
        return this;
    }

    /**
     * Enqueue a mocked output shape using a specific server protocol.
     *
     * @param protocol Protocol to use when serializing the output shape.
     * @param output Output shape to enqueue.
     */
    public MockQueue enqueue(ServerProtocol protocol, SerializableStruct output) {
        queue.offer(new MockedResult.Output(output, protocol));
        return this;
    }

    /**
     * Enqueue a specific exception to be thrown by the transport.
     *
     * @param e Exception to throw.
     */
    public MockQueue enqueueError(RuntimeException e) {
        queue.offer(new MockedResult.Error(e));
        return this;
    }
}
