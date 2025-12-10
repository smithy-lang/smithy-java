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
import software.amazon.smithy.java.http.client.h2.hpack.HeaderField;

/**
 * Processes HTTP/2 response headers and trailers with RFC 9113 validation.
 *
 * <p>This class handles the validation and parsing of response HEADERS frames,
 * including pseudo-header validation, Content-Length tracking, and trailer processing.
 *
 * <h2>RFC 9113 Compliance</h2>
 * <ul>
 *   <li>Section 8.3: Pseudo-headers must appear before regular headers</li>
 *   <li>Section 8.3.2: Response must have exactly one :status pseudo-header</li>
 *   <li>Section 8.3: Request pseudo-headers not allowed in responses</li>
 *   <li>Section 8.1: Trailers must not contain pseudo-headers</li>
 *   <li>Section 8.1.1: Content-Length validation, 1xx responses must not have END_STREAM</li>
 * </ul>
 */
final class H2ResponseHeaderProcessor {

    /** Request pseudo-headers (only allowed in requests, not responses). */
    private static final Set<String> REQUEST_PSEUDO_HEADERS = Set.of(
            ":method",
            ":scheme",
            ":authority",
            ":path");

    /** Result of processing response headers. */
    record Result(HttpHeaders headers, int statusCode, long contentLength) {
        /** Indicates an informational (1xx) response that should be skipped. */
        static final Result INFORMATIONAL = new Result(null, -1, -1);

        boolean isInformational() {
            return this == INFORMATIONAL;
        }
    }

    private H2ResponseHeaderProcessor() {}

    /**
     * Process response headers with full RFC 9113 validation.
     *
     * @param fields the decoded header fields
     * @param streamId the stream ID (for error messages)
     * @param isEndStream whether END_STREAM flag was set
     * @return the processing result, or {@link Result#INFORMATIONAL} for 1xx responses
     * @throws H2Exception if headers violate RFC 9113
     * @throws IOException if headers are malformed
     */
    static Result processResponseHeaders(List<HeaderField> fields, int streamId, boolean isEndStream)
            throws IOException {
        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        int parsedStatusCode = -1;
        boolean seenRegularHeader = false;
        long contentLength = -1;

        for (HeaderField field : fields) {
            String name = field.name();
            String value = field.value();

            if (name.startsWith(":")) {
                // RFC 9113 Section 8.3: All pseudo-headers MUST appear before regular headers
                if (seenRegularHeader) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Pseudo-header '" + name + "' appears after regular header");
                }

                if (name.equals(PSEUDO_STATUS)) {
                    // RFC 9113 Section 8.3.2: Response MUST have exactly one :status
                    if (parsedStatusCode != -1) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Expected a single :status header");
                    }
                    try {
                        parsedStatusCode = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid :status value: " + value);
                    }
                } else if (REQUEST_PSEUDO_HEADERS.contains(name)) {
                    // RFC 9113 Section 8.3: Request pseudo-headers are NOT allowed in responses
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Request pseudo-header '" + name + "' in response");
                } else {
                    // Unknown pseudo-header - RFC 9113 says endpoints MUST treat as malformed
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Unknown pseudo-header '" + name + "' in response");
                }
            } else {
                // Handle a regular header
                seenRegularHeader = true;
                // Track Content-Length for validation per RFC 9113 Section 8.1.1
                if ("content-length".equals(name)) {
                    try {
                        long parsedLength = Long.parseLong(value);
                        if (contentLength != -1 && contentLength != parsedLength) {
                            throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Multiple Content-Length values");
                        }
                        contentLength = parsedLength;
                    } catch (NumberFormatException e) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Invalid Content-Length value: " + value);
                    }
                }

                headers.addHeader(name, value);
            }
        }

        if (parsedStatusCode == -1) {
            throw new IOException("Response missing :status pseudo-header");
        }

        // Check if this is an informational (1xx) response, and skip and wait for final response
        if (parsedStatusCode >= 100 && parsedStatusCode < 200) {
            if (isEndStream) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "1xx response must not have END_STREAM");
            }
            return Result.INFORMATIONAL;
        }

        return new Result(headers, parsedStatusCode, contentLength);
    }

    /**
     * Process trailer headers per RFC 9113 Section 8.1.
     *
     * <p>Trailers are HEADERS sent after DATA with END_STREAM. They MUST NOT
     * contain pseudo-headers.
     *
     * @param fields the decoded header fields
     * @param streamId the stream ID (for error messages)
     * @return the trailer headers
     * @throws H2Exception if trailers contain pseudo-headers
     */
    static HttpHeaders processTrailers(List<HeaderField> fields, int streamId) throws IOException {
        ModifiableHttpHeaders trailers = HttpHeaders.ofModifiable();
        for (HeaderField field : fields) {
            String name = field.name();
            // RFC 9113 Section 8.1: Trailers MUST NOT contain pseudo-headers
            if (name.startsWith(":")) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Trailer contains pseudo-header '" + name + "'");
            }
            trailers.addHeader(name, field.value());
        }
        return trailers;
    }

    /**
     * Validate Content-Length matches actual data received per RFC 9113 Section 8.1.1.
     *
     * @param expectedContentLength expected content length (-1 if not specified)
     * @param receivedContentLength actual bytes received
     * @param streamId the stream ID (for error messages)
     * @throws H2Exception if there is a mismatch
     */
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
