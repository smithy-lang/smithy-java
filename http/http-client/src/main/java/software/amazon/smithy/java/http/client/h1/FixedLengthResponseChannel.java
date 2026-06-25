/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;

final class FixedLengthResponseChannel implements ReadableByteChannel {
    private final H1Exchange h1Exchange;
    private final UnsyncBufferedInputStream buffered;
    private final ReadableByteChannel channel;
    private long remaining;
    private boolean open = true;
    private boolean completed;

    FixedLengthResponseChannel(
            H1Exchange h1Exchange,
            UnsyncBufferedInputStream buffered,
            ReadableByteChannel channel,
            long remaining
    ) {
        this.h1Exchange = h1Exchange;
        this.buffered = buffered;
        this.channel = channel;
        this.remaining = remaining;
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        if (completed) {
            return -1;
        }
        if (!open) {
            throw new ClosedChannelException();
        }
        if (!dst.hasRemaining()) {
            return 0;
        }
        if (remaining == 0) {
            finish();
            return -1;
        }

        int originalLimit = dst.limit();
        if (remaining < dst.remaining()) {
            dst.limit(dst.position() + (int) remaining);
        }
        try {
            int total = drainBuffered(dst);
            if (dst.hasRemaining() && remaining > 0) {
                int n = channel.read(dst);
                if (n < 0) {
                    if (total != 0) {
                        return total;
                    }
                    throw prematureEof();
                }
                total += n;
                remaining -= n;
            }
            if (remaining == 0) {
                finish();
            }
            return total;
        } finally {
            dst.limit(originalLimit);
        }
    }

    private int drainBuffered(ByteBuffer dst) {
        int bufferedBytes = Math.min(buffered.buffered(), dst.remaining());
        if (bufferedBytes == 0) {
            return 0;
        }
        dst.put(buffered.buffer(), buffered.position(), bufferedBytes);
        buffered.consume(bufferedBytes);
        remaining -= bufferedBytes;
        return bufferedBytes;
    }

    @Override
    public boolean isOpen() {
        return open && !completed;
    }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            try {
                if (remaining > 0) {
                    buffered.discard(remaining);
                    remaining = 0;
                }
            } finally {
                finish();
            }
        }
    }

    private void finish() throws IOException {
        if (!completed) {
            completed = true;
            open = false;
            h1Exchange.close();
        }
    }

    private IOException prematureEof() {
        return new IOException("Premature EOF: expected " + remaining
                + " more bytes based on Content-Length");
    }
}
