/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import java.net.URI;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;
import software.amazon.smithy.java.server.core.HttpJob;
import software.amazon.smithy.java.server.core.HttpResponse;
import software.amazon.smithy.java.server.core.Orchestrator;
import software.amazon.smithy.java.server.core.ProtocolResolver;
import software.amazon.smithy.java.server.core.ServiceProtocolResolutionRequest;
import software.amazon.smithy.java.server.exceptions.UnknownOperationException;

final class HttpRequestHandler extends ChannelDuplexHandler {

    private final Orchestrator orchestrator;
    private final ProtocolResolver resolver;
    private HttpJob job;

    HttpRequestHandler(Orchestrator orchestrator, ProtocolResolver resolver) {
        this.orchestrator = orchestrator;
        this.resolver = resolver;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel channel = ctx.channel();
        if (msg instanceof HttpRequest httpRequest) {
            URI uri = URI.create(httpRequest.uri());
            HttpHeaders requestHeaders = new NettyHttpHeaders(httpRequest.headers());

            software.amazon.smithy.java.server.core.HttpRequest request = new software.amazon.smithy.java.server.core.HttpRequest(
                requestHeaders,
                uri,
                httpRequest.method().name()
            );

            try {
                var resolutionResult = resolver.resolve(
                    new ServiceProtocolResolutionRequest(uri, requestHeaders, request.context())
                );
                var response = new HttpResponse(new NettyHttpHeaders());
                this.job = new HttpJob(resolutionResult.operation(), resolutionResult.protocol(), request, response);
            } catch (UnknownOperationException e) {
                var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response);
                channel.close();
                reset(channel);
                return;
            }
        } else if (msg instanceof LastHttpContent content) {
            byte[] bytes = new byte[content.content().readableBytes()];
            content.content().readBytes(bytes);
            content.release();
            job.request().setDataStream(DataStream.ofBytes(bytes));
            orchestrator.enqueue(job).whenCompleteAsync((r, t) -> writeResponse(channel, job), channel.eventLoop());
        }
    }

    private void reset(Channel channel) {
        this.job = null;
    }

    private void writeResponse(Channel channel, HttpJob job) {
        var serializedValue = job.response().getSerializedValue();
        DefaultFullHttpResponse response = null;
        try {
            response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(serializedValue.asByteBuffer().get())
            );
            response.headers().set(((NettyHttpHeaders) job.response().headers()).getNettyHeaders());
            response.headers().set("Content-Length", serializedValue.contentLength());
        } catch (Throwable e) {
            response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR
            );
        }
        channel.writeAndFlush(response);
    }
}
