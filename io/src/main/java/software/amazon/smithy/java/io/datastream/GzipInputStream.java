/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.io.datastream;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.GZIPOutputStream;

public class GzipInputStream extends InputStream {
    private final PipedInputStream pipedIn;
    private final Thread compressionThread;
    private volatile IOException compressionException;

    public GzipInputStream(InputStream source) throws IOException {
        this.pipedIn = new PipedInputStream();
        PipedOutputStream pipedOut = new PipedOutputStream(pipedIn);

        this.compressionThread = Thread.ofVirtual().start(() -> {
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(pipedOut);
                    InputStream in = source) {
                in.transferTo(gzipOut);
            } catch (IOException e) {
                compressionException = e;
            }
        });
    }

    @Override
    public int read() throws IOException {
        checkCompressionException();
        return pipedIn.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        checkCompressionException();
        return pipedIn.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        pipedIn.close();
        compressionThread.interrupt();
        try {
            compressionThread.join(1000); // Wait max 1 second
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkCompressionException() throws IOException {
        if (compressionException != null) {
            throw compressionException;
        }
    }
}
