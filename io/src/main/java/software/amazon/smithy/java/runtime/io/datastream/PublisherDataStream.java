/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class PublisherDataStream implements DataStream {

    private final Flow.Publisher<ByteBuffer> publisher;
    private final long contentLength;
    private final String contentType;
    private final boolean isReplayable;

    PublisherDataStream(Flow.Publisher<ByteBuffer> publisher, long contentLength, String contentType, boolean replay) {
        this.publisher = publisher;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.isReplayable = replay;
    }

    @Override
    public boolean isReplayable() {
        return isReplayable;
    }

    @Override
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        publisher.subscribe(subscriber);
    }
}
