/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.function.Supplier;
import software.amazon.smithy.java.http.client.DelegatedClosingOutputStream;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Per-stream outbound body state.
 *
 * <p>Owns the pooled request-body staging buffer and the stream-facing output
 * stream. This keeps upload-specific buffer ownership local to the stream
 * instead of spreading it across {@link H2Exchange} and {@link H2DataOutputStream}.
 */
final class H2StreamRequestBody {
    private static final int DIRECT_REPLAYABLE_UPLOAD_LIMIT = 8 * 1024 * 1024;

    private final H2Exchange exchange;
    private final H2Muxer muxer;
    private final Runnable onRequestStreamClosed;
    private final Supplier<Boolean> endStreamSent;
    private volatile OutputStream requestOut;

    H2StreamRequestBody(
            H2Exchange exchange,
            H2Muxer muxer,
            Runnable onRequestStreamClosed,
            Supplier<Boolean> endStreamSent
    ) {
        this.exchange = exchange;
        this.muxer = muxer;
        this.onRequestStreamClosed = onRequestStreamClosed;
        this.endStreamSent = endStreamSent;
    }

    synchronized OutputStream outputStream() {
        if (requestOut == null) {
            H2DataOutputStream rawOut = endStreamSent.get()
                    ? new H2DataOutputStream(exchange, muxer, 0)
                    : new H2DataOutputStream(exchange, muxer, muxer.getRemoteMaxFrameSize());
            requestOut = new DelegatedClosingOutputStream(rawOut, rw -> {
                rw.close();
                onRequestStreamClosed.run();
            });
        }
        return requestOut;
    }

    void writeRequestBody(DataStream body) throws IOException {
        if (body == null || body.contentLength() == 0) {
            outputStream().close();
            return;
        }

        if (body.isReplayable() && body.hasKnownLength() && body.contentLength() <= Integer.MAX_VALUE) {
            try {
                if (body.contentLength() <= DIRECT_REPLAYABLE_UPLOAD_LIMIT) {
                    exchange.writeReplayableBody(body.asByteBuffer(), true);
                } else {
                    try (ReadableByteChannel channel = body.asChannel()) {
                        exchange.writeChannelData(channel, body.contentLength(), true);
                    }
                }
            } finally {
                onRequestStreamClosed.run();
                body.close();
            }
            return;
        }

        try (OutputStream out = outputStream()) {
            body.asInputStream().transferTo(out);
        } finally {
            body.close();
        }
    }

    synchronized void closeIfOpen() throws IOException {
        if (requestOut != null && !endStreamSent.get()) {
            requestOut.close();
        }
    }
}
