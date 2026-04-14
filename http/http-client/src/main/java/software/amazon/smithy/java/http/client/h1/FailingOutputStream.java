/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that immediately throws a pre-existing exception on any write operation.
 * Used when Expect: 100-continue fails before body transmission.
 *
 * <p>Close is a no-op since there's nothing to clean up. The exception is thrown when the caller attempts to write,
 * not when they clean up.
 */
final class FailingOutputStream extends OutputStream {
    private final IOException exception;

    FailingOutputStream(IOException exception) {
        this.exception = exception;
    }

    @Override
    public void write(int b) throws IOException {
        throw exception;
    }

    @Override
    public void flush() throws IOException {
        throw exception;
    }

    @Override
    public void close() {
        // No-op: nothing to close
    }
}
