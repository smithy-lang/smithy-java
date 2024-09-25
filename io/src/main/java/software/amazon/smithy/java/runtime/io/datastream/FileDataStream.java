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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

final class FileDataStream implements DataStream {

    private final Path file;
    private final String contentType;
    private final HttpRequest.BodyPublisher publisher;

    FileDataStream(Path file, String contentType) {
        this.file = file;
        this.contentType = contentType;

        try {
            // Eagerly create the publisher since it checks for file existence and gets the content-length.
            publisher = HttpRequest.BodyPublishers.ofFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ByteBuffer waitForByteBuffer() {
        try {
            return ByteBuffer.wrap(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isReplayable() {
        return true;
    }

    @Override
    public CompletableFuture<InputStream> asInputStream() {
        try {
            return CompletableFuture.completedFuture(Files.newInputStream(file));
        } catch (IOException e) {
            // To match what happens in the publisher.
            return CompletableFuture.failedFuture(new UncheckedIOException(e));
        }
    }

    @Override
    public long contentLength() {
        return publisher.contentLength();
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
