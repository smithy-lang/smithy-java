/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ConnectionAgentH2Exchange implements HttpExchange {

    private static final int PIPE_BUFFER_SIZE = 64 * 1024;

    private final ConnectionAgentH2Connection connection;
    private final HttpRequest request;
    private final CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean requestBodyClosed = new AtomicBoolean(false);
    private final AtomicBoolean responseBodyOpened = new AtomicBoolean(false);
    private final AtomicBoolean sendStarted = new AtomicBoolean(false);
    private final OutputStream requestBody;
    private final PipedInputStream requestPipeIn;
    private final ByteArrayOutputStream bufferedRequestBody;
    private volatile InputStream responseBody;

    ConnectionAgentH2Exchange(ConnectionAgentH2Connection connection, HttpRequest request) throws IOException {
        this.connection = connection;
        this.request = request;

        DataStream originalBody = request.body();
        if (originalBody == null || originalBody.contentLength() == 0) {
            this.requestPipeIn = null;
            this.bufferedRequestBody = null;
            this.requestBody = OutputStream.nullOutputStream();
            this.requestBodyClosed.set(true);
            startSend(request.toModifiableCopy().setBody(DataStream.ofEmpty()).toUnmodifiable());
        } else if (originalBody.isReplayable() && originalBody.hasKnownLength()
                && originalBody.contentLength() <= Integer.MAX_VALUE) {
            this.requestPipeIn = null;
            this.bufferedRequestBody = new ByteArrayOutputStream((int) originalBody.contentLength());
            this.requestBody = new FilterOutputStream(bufferedRequestBody) {
                @Override
                public void close() throws IOException {
                    if (!requestBodyClosed.compareAndSet(false, true)) {
                        return;
                    }
                    super.close();
                    startSend(request.toModifiableCopy()
                            .setBody(DataStream.ofBytes(bufferedRequestBody.toByteArray()))
                            .toUnmodifiable());
                }
            };
        } else {
            this.bufferedRequestBody = null;
            this.requestPipeIn = new PipedInputStream(PIPE_BUFFER_SIZE);
            PipedOutputStream pipeOut = new PipedOutputStream(requestPipeIn);
            this.requestBody = new FilterOutputStream(pipeOut) {
                @Override
                public void close() throws IOException {
                    if (requestBodyClosed.compareAndSet(false, true)) {
                        super.close();
                    }
                }
            };
            DataStream requestStream = DataStream.ofInputStream(
                    requestPipeIn,
                    originalBody.contentType(),
                    originalBody.hasKnownLength() ? originalBody.contentLength() : -1);
            startSend(request.toModifiableCopy().setBody(requestStream).toUnmodifiable());
        }
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public OutputStream requestBody() {
        return requestBody;
    }

    @Override
    public void writeRequestBody(DataStream body) throws IOException {
        if (bufferedRequestBody != null && body != null && body.isReplayable() && body.hasKnownLength()) {
            requestBodyClosed.set(true);
            startSend(request.toModifiableCopy().setBody(body).toUnmodifiable());
            return;
        }
        HttpExchange.super.writeRequestBody(body);
    }

    @Override
    public HttpVersion responseVersion() throws IOException {
        return awaitResponse().httpVersion();
    }

    @Override
    public int responseStatusCode() throws IOException {
        return awaitResponse().statusCode();
    }

    @Override
    public InputStream responseBody() throws IOException {
        if (responseBodyOpened.compareAndSet(false, true)) {
            responseBody = awaitResponse().body().asInputStream();
        }
        return responseBody;
    }

    @Override
    public ReadableByteChannel responseBodyChannel() throws IOException {
        return awaitResponse().body().asChannel();
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        return awaitResponse().headers();
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return true;
    }

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        IOException first = null;
        try {
            requestBody.close();
        } catch (IOException e) {
            first = e;
        }
        if (responseBody != null) {
            try {
                responseBody.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (requestPipeIn != null) {
            try {
                requestPipeIn.close();
            } catch (IOException e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private HttpResponse awaitResponse() throws IOException {
        if (!sendStarted.get() && bufferedRequestBody != null && requestBodyClosed.get()) {
            startSend(request.toModifiableCopy()
                    .setBody(DataStream.ofBytes(bufferedRequestBody.toByteArray()))
                    .toUnmodifiable());
        }
        try {
            return responseFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for response", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Exchange failed", cause);
        }
    }

    private void startSend(HttpRequest wireRequest) {
        if (!sendStarted.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                responseFuture.complete(connection.send(wireRequest));
            } catch (Throwable t) {
                responseFuture.completeExceptionally(t);
            }
        });
    }
}
