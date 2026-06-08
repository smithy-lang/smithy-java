/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Translates a completed {@link HttpJob} (or a failure that prevented
 * it from completing) into the headers and body a transport writes
 * back to the wire. Transports invoke this from their response-writing
 * callback and translate the resulting {@link SerializedResponse} into
 * their own response object (Netty {@code DefaultFullHttpResponse},
 * Vert.x {@code HttpServerResponse}, Servlet {@code HttpServletResponse},
 * etc.).
 *
 <p>Owns the status-code + header-copy + content-type/length logic
 * shared between Netty's response writer and the Vert.x server's
 * response writer.
 *
 * <p>The serialized body is exposed via {@link SerializedResponse#body()}
 * as the original {@link DataStream} so transports that can write
 * directly from a {@link java.nio.ByteBuffer} (Netty's
 * {@code Unpooled.wrappedBuffer}) avoid the memcpy that
 * {@code byte[]}-based transports (Vert.x's {@code Buffer.buffer(byte[])})
 * incur anyway.
 */
public final class HttpResponseSerializer {

    private HttpResponseSerializer() {}

    /**
     * Build a wire-ready response from a job and (optional) failure.
     *
     * <ul>
     *   <li>{@code failure != null}: a 500 response with no headers
     *       and a {@code null} body. Transports typically log; this
     *       class does not.</li>
     *   <li>{@code failure == null}: status code from the job
     *       (defaulting to 200 if unset), headers copied from
     *       {@link HttpJob#response()}, content-type filled in from
     *       the serialized payload's media type if not already set,
     *       content-length filled in from the payload's byte length.
     *       Body is the payload's {@link DataStream}; may be
     *       {@code null} if the job set no serialized value.</li>
     * </ul>
     */
    public static SerializedResponse from(HttpJob job, Throwable failure) {
        if (failure != null) {
            return new SerializedResponse(500, HttpHeaders.ofModifiable(), null);
        }

        // ArrayHttpHeaders.map() returns an immutable view; copy
        // before mutation. Keys are already lowercased by the headers
        // impl, so plain containsKey suffices for the auto-fill
        // checks below.
        Map<String, List<String>> headersMap = new LinkedHashMap<>(job.response().headers().map());

        int statusCode = job.response().getStatusCode();
        if (statusCode <= 0) {
            statusCode = 200;
        }

        DataStream body = job.response().getSerializedValue();
        if (body != null) {
            // If the framework didn't already pin a content-type or
            // content-length, take them from the serialized payload's
            // metadata.
            if (body.contentType() != null && !headersMap.containsKey("content-type")) {
                addHeader(headersMap, "content-type", body.contentType());
            }
            if (!headersMap.containsKey("content-length")) {
                addHeader(headersMap, "content-length", Long.toString(body.contentLength()));
            }
        }

        return new SerializedResponse(statusCode, HttpHeaders.ofModifiable(headersMap), body);
    }

    private static void addHeader(Map<String, List<String>> headers, String name, String value) {
        List<String> values = new ArrayList<>(1);
        values.add(value);
        headers.put(name, values);
    }

    /**
     * The wire-ready shape of an HTTP response. Transports translate
     * this into their own framework's response object — the wire
     * version (HTTP/1.1 vs HTTP/2) is the transport's responsibility,
     * not this record's. The {@code body} field is the serialized
     * payload's underlying {@link DataStream} (or {@code null} if
     * none); transports read it via
     * {@link DataStream#asByteBuffer()} or
     * {@link DataStream#asInputStream()}.
     */
    public record SerializedResponse(int statusCode, HttpHeaders headers, DataStream body) {}
}
