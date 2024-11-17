/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

import software.amazon.smithy.java.runtime.io.datastream.DataStream;

/**
 * A modifiable HTTP message.
 */
public interface ModifiableHttpMessage extends HttpMessage {
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
    void setHeaders(HttpHeaders headers);

    /**
     * Set the HTTP body.
     *
     * @param body Body to set.
     */
    void setBody(DataStream body);
}
