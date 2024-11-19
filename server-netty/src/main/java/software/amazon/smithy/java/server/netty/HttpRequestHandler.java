/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.io.datastream.DataStream;
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
    private ByteArrayOutputStream bodyAccumulator;

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
                    new ServiceProtocolResolutionRequest(uri, requestHeaders, request.context(), request.method())
                );
                var response = new HttpResponse(new NettyHttpHeaders());
                this.job = new HttpJob(resolutionResult.operation(), resolutionResult.protocol(), request, response);
                this.bodyAccumulator = new ByteArrayOutputStream();
            } catch (UnknownOperationException e) {
                var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
                ctx.writeAndFlush(response);
                channel.close();
                reset(channel);
            }
        } else if (msg instanceof HttpContent content) {
            // if the job is null, we either failed to select a protocol or prepare the job. in either case,
            // swallow the remaining request payload.
            // TODO: set a max swallow size and just terminate the connection if there's too much to read
            if (job == null) {
                content.release();
                return;
            }

            boolean isLast = content instanceof LastHttpContent;
            content.content().readBytes(bodyAccumulator, content.content().readableBytes());
            content.release();
            if (isLast) {
                job.request()
                    .setDataStream(
                        DataStream.ofBytes(bodyAccumulator.toByteArray(), job.request().headers().contentType())
                    );
                orchestrator.enqueue(job).whenCompleteAsync((r, t) -> writeResponse(channel, job), channel.eventLoop());
            }

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
                HttpResponseStatus.valueOf(job.response().getStatusCode()),
                Unpooled.wrappedBuffer(serializedValue.waitForByteBuffer())
            );
            response.headers().set(((NettyHttpHeaders) job.response().headers()).getNettyHeaders());
            response.headers().set("content-length", serializedValue.contentLength());
            if (serializedValue.contentType() != null) {
                response.headers().set("content-type", serializedValue.contentType());
            }
        } catch (Throwable e) {
            response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR
            );
        }
        channel.writeAndFlush(response);
    }
}
