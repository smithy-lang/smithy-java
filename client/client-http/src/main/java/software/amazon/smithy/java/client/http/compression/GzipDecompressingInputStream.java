/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.GZIPInputStream;

/**
 * An InputStream that decompresses GZIP data from a source InputStream.
 * The GZIP header is read on the first read rather than on construction, and an empty source reads as empty.
 */
final class GzipDecompressingInputStream extends InputStream {
    private final InputStream source;
    private InputStream decoded;

    GzipDecompressingInputStream(InputStream source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        return decoded().read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return decoded().read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (decoded != null) {
            decoded.close();
        } else {
            source.close();
        }
    }

    private InputStream decoded() throws IOException {
        if (decoded == null) {
            var pushback = new PushbackInputStream(source, 1);
            int first = pushback.read();
            if (first == -1) {
                decoded = InputStream.nullInputStream();
            } else {
                pushback.unread(first);
                decoded = new GZIPInputStream(pushback);
            }
        }
        return decoded;
    }
}
