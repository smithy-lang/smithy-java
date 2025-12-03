/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that immediately throws a pre-existing exception on any operation.
 * Used when Expect: 100-continue fails before body transmission.
 */
class FailingOutputStream extends OutputStream {
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
    public void close() throws IOException {
        throw exception;
    }
}
