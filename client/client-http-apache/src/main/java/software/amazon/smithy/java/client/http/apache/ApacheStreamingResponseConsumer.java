/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.apache.hc.core5.http.nio.CapacityChannel;
import org.apache.hc.core5.http.protocol.HttpContext;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ApacheStreamingResponseConsumer implements AsyncResponseConsumer<ApacheHttpResponse> {
    private static final int SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD = 64 * 1024;

    private final CompletableFuture<ApacheHttpResponse> responseFuture = new CompletableFuture<>();
    private final AtomicReference<FutureCallback<ApacheHttpResponse>> callbackRef = new AtomicReference<>();
    private final int readBufferSize;
    private HttpVersion responseVersion;
    private int statusCode;
    private ApacheHttpHeaders headers;
    private String contentTypeHeader;
    private long contentLength = -1;
    private byte[] smallBody;
    private int smallBodyPosition;
    private boolean aggregateSmallBody;
    private ApacheSharedInputBuffer sharedInputBuffer;
    private volatile Throwable failure;

    ApacheStreamingResponseConsumer(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    CompletableFuture<ApacheHttpResponse> responseFuture() {
        return responseFuture;
    }

    @Override
    public void consumeResponse(
            HttpResponse response,
            EntityDetails entityDetails,
            HttpContext context,
            FutureCallback<ApacheHttpResponse> resultCallback
    ) throws HttpException, IOException {
        callbackRef.set(resultCallback);
        responseVersion = ApacheResponses.toSmithyVersion(response.getVersion());
        statusCode = response.getCode();
        headers = new ApacheHttpHeaders(response.getHeaders());
        contentTypeHeader = headers.contentType();
        Long headerContentLength = headers.contentLength();
        contentLength = headerContentLength == null ? -1 : headerContentLength;
        aggregateSmallBody = entityDetails != null
                && contentLength > 0
                && contentLength <= SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD;
        if (aggregateSmallBody) {
            smallBody = new byte[(int) contentLength];
            return;
        }
        sharedInputBuffer = entityDetails != null ? new ApacheSharedInputBuffer(readBufferSize) : null;
        var bodyStream = new ApacheSharedInputStream(sharedInputBuffer, this);
        DataStream body = DataStream.ofInputStream(bodyStream, contentTypeHeader, contentLength);
        responseFuture.complete(new ApacheHttpResponse(responseVersion, statusCode, headers, body));
        if (entityDetails == null) {
            completeTransport();
        }
    }

    @Override
    public void informationResponse(HttpResponse response, HttpContext context) throws HttpException, IOException {}

    @Override
    public void updateCapacity(CapacityChannel capacityChannel) throws IOException {
        if (aggregateSmallBody) {
            capacityChannel.update(readBufferSize);
            return;
        }
        if (sharedInputBuffer != null) {
            sharedInputBuffer.updateCapacity(capacityChannel);
        }
    }

    @Override
    public void consume(ByteBuffer src) throws IOException {
        if (!src.hasRemaining()) {
            return;
        }
        if (aggregateSmallBody) {
            int remaining = src.remaining();
            src.get(smallBody, smallBodyPosition, remaining);
            smallBodyPosition += remaining;
            return;
        }
        if (sharedInputBuffer != null) {
            sharedInputBuffer.fill(src);
        }
    }

    @Override
    public void streamEnd(List<? extends Header> trailers) throws HttpException, IOException {
        if (aggregateSmallBody) {
            DataStream body = DataStream.ofBytes(smallBody, 0, smallBodyPosition, contentTypeHeader);
            var response = new ApacheHttpResponse(responseVersion, statusCode, headers, body);
            responseFuture.complete(response);
            completeTransport();
            return;
        }
        if (sharedInputBuffer != null) {
            sharedInputBuffer.markEndStream();
        } else {
            completeTransport();
        }
    }

    @Override
    public void failed(Exception cause) {
        failure = cause;
        if (sharedInputBuffer != null) {
            sharedInputBuffer.abort();
        }
        var callback = callbackRef.getAndSet(null);
        if (callback != null) {
            callback.failed(cause);
        }
        responseFuture.completeExceptionally(cause);
    }

    @Override
    public void releaseResources() {
        smallBody = null;
    }

    IOException failureAsIOException() {
        var cause = failure;
        if (cause == null) {
            return null;
        }
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException(cause);
    }

    void completeTransport() {
        var callback = callbackRef.getAndSet(null);
        if (callback != null) {
            var response = responseFuture.getNow(null);
            callback.completed(response);
        }
    }
}
