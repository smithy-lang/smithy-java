/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestCapturingHttp2ClientHandler implements Http2ClientHandler {
    private Throwable cause;
    private final Map<String, List<String>> capturedHeaders = new HashMap<>();
    private final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var headers = frame.headers();
        for (var kvp : headers) {
            var key = kvp.getKey().toString();
            var value = kvp.getValue().toString();
            capturedHeaders.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {
        try {
            var content = frame.content();
            var bytes = new byte[content.readableBytes()];
            content.readBytes(bytes);
            capturedBody.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        this.cause = cause;
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
