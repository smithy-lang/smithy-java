/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

/**
 * Marker interface for HTTP message bodies that support trailer headers.
 *
 * <p>Response bodies from HTTP/2 or chunked HTTP/1.1 transfers may carry
 * trailing headers after the body data. Call {@link #trailerHeaders()} after
 * the body is fully read to access them.
 *
 * <p>Request bodies can implement this interface to provide trailing headers
 * that are sent after the request body is fully written (e.g., streaming checksums).
 *
 * <p>Usage:
 * {@snippet :
 * var response = client.send(request);
 * response.body().asInputStream().readAllBytes();
 * if (response.body() instanceof TrailerSupport ts) {
 *     HttpHeaders trailers = ts.trailerHeaders();
 * }
 * }
 */
public interface TrailerSupport {
    /**
     * Get trailer headers.
     *
     * <p>For response bodies, this blocks until the body is fully read and trailers
     * are available. For request bodies, this is evaluated after the body is fully written.
     *
     * @return trailer headers, or empty headers if none present
     */
    HttpHeaders trailerHeaders();
}
