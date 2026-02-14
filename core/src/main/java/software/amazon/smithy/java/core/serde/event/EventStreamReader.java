/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.serde.event;

import java.util.Iterator;
import java.util.NoSuchElementException;
import software.amazon.smithy.java.core.schema.SerializableStruct;

/**
 * Interface for reading events from an event stream.
 *
 * @param <T> The type of the event stream.
 */
public sealed interface EventStreamReader<T extends SerializableStruct>
        extends Iterable<T>, AutoCloseable, EventStream<T> permits InternalEventStreamReader {

    /**
     * Reads an event from the event stream.
     *
     * @return the next event in the stream or <code>null</code> if the stream has reached its end.
     */
    T read();

    /**
     * Returns a new iterator to iterate over the events in the stream.
     *
     * @return a new iterator to iterate over the events in the stream.
     */
    @Override
    default Iterator<T> iterator() {
        return new Iterator<>() {
            private T next = null;

            @Override
            public boolean hasNext() {
                if (next != null)
                    return true;
                next = read();
                return next != null;
            }

            @Override
            public T next() {
                if (next == null)
                    throw new NoSuchElementException();
                T result = next;
                next = null;
                return result;
            }
        };
    }
}
