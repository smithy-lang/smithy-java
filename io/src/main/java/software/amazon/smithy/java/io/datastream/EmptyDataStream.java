/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

final class EmptyDataStream implements DataStream {

    static final EmptyDataStream INSTANCE = new EmptyDataStream();
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final Flow.Publisher<ByteBuffer> PUBLISHER = HttpRequest.BodyPublishers.noBody();

    @Override
    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(EMPTY_BYTES);
    }

    @Override
    public InputStream asInputStream() {
        return InputStream.nullInputStream();
    }

    @Override
    public void writeTo(OutputStream out) {
        // No-op
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long contentLength() {
        return 0;
    }

    @Override
    public String contentType() {
        return null;
    }

    @Override
    public boolean hasKnownLength() {
        return true;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        PUBLISHER.subscribe(subscriber);
    }
}
