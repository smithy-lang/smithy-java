/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_PROTOCOL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_STATUS;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;

/**
 * Processes HTTP/2 response headers and trailers with RFC 9113 validation.
 *
 * <p>Headers are passed as flat List&lt;String&gt;: [name0, value0, name1, value1, ...].
 */
final class H2ResponseHeaderProcessor {

    private static final Set<String> REQUEST_PSEUDO_HEADERS = Set.of(
            ":method",
            ":scheme",
            ":authority",
            ":path");

    record Result(HttpHeaders headers, int statusCode, long contentLength) {
        static final Result INFORMATIONAL = new Result(null, -1, -1);

        boolean isInformational() {
            return this == INFORMATIONAL;
        }
    }

    private H2ResponseHeaderProcessor() {}

    /**
     * Process response headers.
     *
     * @param fields flat list [name0, value0, name1, value1, ...]
     */
    static Result processResponseHeaders(List<String> fields, int streamId, boolean isEndStream)
            throws IOException {
        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        int parsedStatusCode = -1;
        boolean seenRegularHeader = false;
        long contentLength = -1;

        for (int i = 0; i < fields.size(); i += 2) {
            String name = fields.get(i);
            String value = fields.get(i + 1);

            if (name.startsWith(":")) {
                if (seenRegularHeader) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Pseudo-header '" + name + "' appears after regular header");
                }

                if (name.equals(PSEUDO_STATUS)) {
                    if (parsedStatusCode != -1) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Expected a single :status header");
                    }
                    try {
                        parsedStatusCode = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid :status value: " + value);
                    }
                } else if (REQUEST_PSEUDO_HEADERS.contains(name)) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Request pseudo-header '" + name + "' in response");
                } else {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Unknown pseudo-header '" + name + "' in response");
                }
            } else {
                seenRegularHeader = true;
                if ("content-length".equals(name)) {
                    try {
                        long parsedLength = Long.parseLong(value);
                        if (contentLength != -1 && contentLength != parsedLength) {
                            throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Multiple Content-Length values");
                        }
                        contentLength = parsedLength;
                    } catch (NumberFormatException e) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Invalid Content-Length: " + value);
                    }
                }
                headers.addHeader(name, value);
            }
        }

        if (parsedStatusCode == -1) {
            throw new IOException("Response missing :status pseudo-header");
        }

        if (parsedStatusCode >= 100 && parsedStatusCode < 200) {
            if (isEndStream) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "1xx response must not have END_STREAM");
            }
            return Result.INFORMATIONAL;
        }

        return new Result(headers, parsedStatusCode, contentLength);
    }

    /**
     * Process trailer headers.
     *
     * @param fields flat list [name0, value0, name1, value1, ...]
     */
    static HttpHeaders processTrailers(List<String> fields, int streamId) throws IOException {
        ModifiableHttpHeaders trailers = HttpHeaders.ofModifiable();
        for (int i = 0; i < fields.size(); i += 2) {
            String name = fields.get(i);
            if (name.startsWith(":")) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Trailer contains pseudo-header '" + name + "'");
            }
            trailers.addHeader(name, fields.get(i + 1));
        }
        return trailers;
    }

    static void validateContentLength(long expectedContentLength, long receivedContentLength, int streamId)
            throws IOException {
        if (expectedContentLength >= 0 && receivedContentLength != expectedContentLength) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR,
                    streamId,
                    "Content-Length mismatch: expected " + expectedContentLength +
                            " bytes, received " + receivedContentLength + " bytes");
        }
    }
}
