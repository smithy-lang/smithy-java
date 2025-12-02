/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A modifiable HTTP message.
 */
public interface ModifiableHttpMessage extends HttpMessage {
    @Override
    ModifiableHttpHeaders headers();

    /**
     * Set the HTTP version.
     *
     * @param version Version to set.
     */
    void setHttpVersion(HttpVersion version);

    /**
     * Set the HTTP headers.
     *
     * @param headers Headers to set.
     */
    void setHeaders(ModifiableHttpHeaders headers);

    /**
     * Set the HTTP body.
     *
     * <p>If the body has a known content-type and no content-type is set, the content-type header is added to the
     * message. If the body has a known content-length and no content-length header is set, the content-length header
     * is added to the message.
     *
     * @param body Body to set.
     */
    void setBody(DataStream body);
}
