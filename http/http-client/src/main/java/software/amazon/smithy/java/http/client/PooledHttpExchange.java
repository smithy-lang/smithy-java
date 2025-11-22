/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import software.amazon.smithy.java.http.api.HttpHeaders;

/**
 * Wrapper around HttpExchange that returns connection to pool on close.
 */
final class PooledHttpExchange implements HttpExchange {
    private final HttpExchange delegate;
    private final HttpConnection connection;
    private final ConnectionPool pool;
    private final URI uri;
    private boolean closed = false;
    private boolean errored = false; // Track if any error occurred

    PooledHttpExchange(
            HttpExchange delegate,
            HttpConnection connection,
            ConnectionPool pool,
            URI uri
    ) {
        this.delegate = delegate;
        this.connection = connection;
        this.pool = pool;
        this.uri = uri;
    }

    @Override
    public OutputStream requestBody() {
        return delegate.requestBody();
    }

    @Override
    public InputStream responseBody() throws IOException {
        try {
            return delegate.responseBody();
        } catch (IOException e) {
            errored = true; // Mark as errored - connection is corrupted
            throw e;
        }
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        try {
            return delegate.responseHeaders();
        } catch (IOException e) {
            errored = true; // Mark as errored - connection is corrupted
            throw e;
        }
    }

    @Override
    public int statusCode() throws IOException {
        try {
            return delegate.statusCode();
        } catch (IOException e) {
            errored = true; // Mark as errored - connection is corrupted
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;

        try {
            delegate.close();

            // If any error occurred during the exchange, evict the connection
            // as it may be in a corrupted/inconsistent state
            if (errored) {
                pool.evict(connection, uri);
            } else {
                pool.release(connection, uri);
            }
        } catch (IOException e) {
            // Error during close - definitely evict
            pool.evict(connection, uri);
            throw e;
        }
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return delegate.supportsBidirectionalStreaming();
    }
}
