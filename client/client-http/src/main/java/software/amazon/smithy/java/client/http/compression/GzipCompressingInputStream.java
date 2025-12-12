/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.compression;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

final class GzipCompressingInputStream extends InputStream {

    private final InputStream source;
    private byte[] compressedData;
    private int position = 0;

    public GzipCompressingInputStream(InputStream source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        ensureCompressed();
        return position < compressedData.length ? compressedData[position++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        ensureCompressed();
        var available = compressedData.length - position;
        if (available <= 0)
            return -1;

        var toRead = Math.min(len, available);
        System.arraycopy(compressedData, position, b, off, toRead);
        position += toRead;
        return toRead;
    }

    private void ensureCompressed() throws IOException {
        if (compressedData == null) {
            var buffer = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                source.transferTo(gzip);
            }
            compressedData = buffer.toByteArray();
        }
    }

    @Override
    public void close() throws IOException {
        source.close();
    }
}
