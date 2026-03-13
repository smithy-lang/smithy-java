/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

final class InputStreamDataStream implements DataStream {

    private final InputStream inputStream;
    private final String contentType;
    private final long contentLength;
    private boolean consumed;
    private boolean closed;

    InputStreamDataStream(InputStream inputStream, String contentType, long contentLength) {
        this.inputStream = inputStream;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    @Override
    public InputStream asInputStream() {
        if (consumed) {
            throw new IllegalStateException("DataStream is not replayable and has already been consumed");
        }
        consumed = true;
        return inputStream;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        asInputStream().transferTo(out);
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
    public long contentLength() {
        return contentLength;
    }

    @Override
    public String contentType() {
        return contentType;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close data stream", e);
            }
        }
    }
}
