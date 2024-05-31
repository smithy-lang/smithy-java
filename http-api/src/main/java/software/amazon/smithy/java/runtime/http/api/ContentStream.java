/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

/**
 * A rewindable stream of data for HTTP requests.
 */
public interface ContentStream {
    /**
     * Get an empty ContentStream.
     */
    ContentStream EMPTY = new ContentStream() {
        @Override
        public Flow.Publisher<ByteBuffer> publisher() {
            // TODO: Is this a valid "empty" publisher?
            return subscriber -> {};
        }

        @Override
        public boolean rewind() {
            return false;
        }
    };

    // TODO: change to publisher
    /**
     * Get the Flow.Publisher of ByteBuffer.
     *
     * @return the underlying Publisher.
     */
    Flow.Publisher<ByteBuffer> publisher();

    // TODO: Not sure if this needs to be rewindable with Flow?
    /**
     * Attempt to rewind the input stream to the beginning of the stream.
     *
     * <p>This method must not throw if the stream is not rewindable.
     *
     * @return Returns true if the stream could be rewound.
     */
    boolean rewind();
}
