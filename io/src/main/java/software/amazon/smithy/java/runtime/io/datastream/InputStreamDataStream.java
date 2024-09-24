/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

final class InputStreamDataStream implements DataStream {

    private final InputStream inputStream;
    private final String contentType;
    private final long contentLength;
    private Flow.Publisher<ByteBuffer> publisher;

    InputStreamDataStream(InputStream inputStream, String contentType, long contentLength) {
        this.inputStream = inputStream;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public ByteBuffer waitForByteBuffer() {
        try {
            return ByteBuffer.wrap(inputStream.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        return CompletableFuture.completedFuture(inputStream);
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
        var p = publisher;
        if (p == null) {
            p = publisher = HttpRequest.BodyPublishers.ofInputStream(() -> inputStream);
        }
        p.subscribe(subscriber);
    }
}
