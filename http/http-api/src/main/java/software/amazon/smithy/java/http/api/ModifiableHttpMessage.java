/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A modifiable HTTP message.
 *
 * @param <T> The concrete modifiable message type for fluent returns.
 */
@SuppressWarnings("unchecked")
public interface ModifiableHttpMessage<T extends ModifiableHttpMessage<T>> extends HttpMessage {
    @Override
    ModifiableHttpHeaders headers();

    /**
     * Set the HTTP version.
     *
     * @param version Version to set.
     * @return this message.
     */
    T setHttpVersion(HttpVersion version);

    /**
     * Set the HTTP headers.
     *
     * @param headers Headers to set.
     * @return this message.
     */
    T setHeaders(ModifiableHttpHeaders headers);

    /**
     * Set a specific header by name and replace any existing value.
     *
     * @param name Header to set.
     * @param value Value to set.
     * @return modifiable message.
     */
    default T setHeader(String name, String value) {
        headers().setHeader(name, value);
        return (T) this;
    }

    /**
     * Add a specific header by name to any existing headers with the same name.
     *
     * @param name Header to add.
     * @param value Value to add.
     * @return modifiable message.
     */
    default T addHeader(String name, String value) {
        headers().addHeader(name, value);
        return (T) this;
    }

    /**
     * Set the HTTP body.
     *
     * <p>If the body has a known content-type and no content-type is set, the content-type header is added to the
     * message. If the body has a known content-length and no content-length header is set, the content-length header
     * is added to the message.
     *
     * @param body Body to set.
     * @return this message.
     */
    T setBody(DataStream body);

    /**
     * Set the body of the message.
     *
     * @param publisher Body to set.
     * @return the builder.
     */
    default T setBody(Flow.Publisher<ByteBuffer> publisher) {
        return setBody(DataStream.ofPublisher(publisher, null, -1));
    }
}
