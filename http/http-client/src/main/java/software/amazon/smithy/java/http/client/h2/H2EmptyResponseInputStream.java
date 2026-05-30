/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class H2EmptyResponseInputStream extends InputStream {
    private final H2Exchange exchange;
    private boolean closed;

    H2EmptyResponseInputStream(H2Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public int read() throws IOException {
        close();
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }
        close();
        return -1;
    }

    @Override
    public long transferTo(OutputStream out) throws IOException {
        close();
        return 0;
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            exchange.onResponseStreamClosed();
        }
    }
}
