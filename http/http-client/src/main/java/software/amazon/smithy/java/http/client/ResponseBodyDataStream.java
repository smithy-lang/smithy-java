/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ResponseBodyDataStream implements DataStream {

    @FunctionalInterface
    interface IOSupplier<T> {
        T get() throws IOException;
    }

    private final IOSupplier<InputStream> inputStreamSupplier;
    private final IOSupplier<ReadableByteChannel> channelSupplier;
    private final String contentType;
    private final long contentLength;
    private boolean consumed;
    private boolean closed;
    private DataStream delegate;

    ResponseBodyDataStream(
            IOSupplier<InputStream> inputStreamSupplier,
            IOSupplier<ReadableByteChannel> channelSupplier,
            String contentType,
            long contentLength
    ) {
        this.inputStreamSupplier = inputStreamSupplier;
        this.channelSupplier = channelSupplier;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream asInputStream() {
        markConsumed();
        return materializeInputStream().asInputStream();
    }

    @Override
    public ReadableByteChannel asChannel() {
        markConsumed();
        return materializeChannel().asChannel();
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        markConsumed();
        materializeInputStream().writeTo(out);
    }

    @Override
    public void writeTo(WritableByteChannel dst) throws IOException {
        markConsumed();
        materializeChannel().writeTo(dst);
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
    public boolean isReplayable() {
        return false;
    }

    @Override
    public boolean isAvailable() {
        return !consumed;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (delegate != null) {
                delegate.close();
            }
        }
    }

    private DataStream materializeInputStream() {
        if (delegate == null) {
            try {
                delegate = inputStreamSupplier != null
                        ? DataStream.ofInputStream(inputStreamSupplier.get(), contentType, contentLength)
                        : DataStream.ofChannel(channelSupplier.get(), contentType, contentLength);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return delegate;
    }

    private DataStream materializeChannel() {
        if (delegate == null) {
            try {
                delegate = channelSupplier != null
                        ? DataStream.ofChannel(channelSupplier.get(), contentType, contentLength)
                        : DataStream.ofInputStream(inputStreamSupplier.get(), contentType, contentLength);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return delegate;
    }

    private void markConsumed() {
        if (consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;
    }
}
