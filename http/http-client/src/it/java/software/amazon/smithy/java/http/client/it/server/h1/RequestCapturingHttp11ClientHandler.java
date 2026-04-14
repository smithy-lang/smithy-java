/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestCapturingHttp11ClientHandler implements Http11ClientHandler {
    private final Map<String, List<String>> capturedHeaders = new HashMap<>();
    private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();
    private Throwable cause;

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        captureHeaders(request);
        captureBody(request.content());
    }

    @Override
    public void onRequest(ChannelHandlerContext ctx, HttpRequest request) {
        captureHeaders(request);
    }

    @Override
    public void onContent(ChannelHandlerContext ctx, HttpContent content) {
        captureBody(content.content());
    }

    @Override
    public void onLastContent(ChannelHandlerContext ctx, LastHttpContent content) {
        captureBody(content.content());
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        this.cause = cause;
    }

    private void captureHeaders(HttpRequest request) {
        var headers = request.headers();
        for (var kvp : headers) {
            capturedHeaders.computeIfAbsent(kvp.getKey(), k -> new ArrayList<>()).add(kvp.getValue());
        }
    }

    private void captureBody(ByteBuf content) {
        var bytes = new byte[content.readableBytes()];
        content.getBytes(content.readerIndex(), bytes); // Don't consume the buffer
        try {
            capturedBody.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Throwable cause() {
        return cause;
    }

    public Map<String, List<String>> capturedHeaders() {
        return capturedHeaders;
    }

    public ByteArrayOutputStream capturedBody() {
        return capturedBody;
    }
}
