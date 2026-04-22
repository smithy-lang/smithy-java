/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    public ByteBuffer asByteBuffer() {
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
    public boolean isAvailable() {
        return true;
    }

    @Override
    public InputStream asInputStream() {
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ReadableByteChannel asChannel() {
        try {
            return FileChannel.open(file, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        try (InputStream in = asInputStream()) {
            in.transferTo(out);
        }
    }

    @Override
    public void writeTo(WritableByteChannel channel) throws IOException {
        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = 0;
            long size = fileChannel.size();
            while (position < size) {
                long transferred = fileChannel.transferTo(position, size - position, channel);
                if (transferred <= 0) {
                    fileChannel.position(position);
                    copyRemaining(fileChannel, channel);
                    break;
                }
                position += transferred;
            }
        }
    }

    private static void copyRemaining(FileChannel fileChannel, WritableByteChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);
        while (fileChannel.read(buffer) >= 0) {
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            buffer.clear();
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
