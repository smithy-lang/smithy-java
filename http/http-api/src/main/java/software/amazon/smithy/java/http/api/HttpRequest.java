/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * HTTP request.
 */
public interface HttpRequest extends HttpMessage {
    /**
     * Get the method of the request.
     *
     * @return the method.
     */
    String method();

    /**
     * Get the URI of the request.
     *
     * @return the request URI.
     */
    SmithyUri uri();

    /**
     * Get a modifiable version of the request, or returns the current request if it's already modifiable.
     *
     * @return the modifiable request or current request.
     */
    ModifiableHttpRequest toModifiable();

    /**
     * Creates a modifiable copy of the request, even if the current request is modifiable.
     *
     * @return the modifiable copy of this request.
     */
    ModifiableHttpRequest toModifiableCopy();

    /**
     * Creates an unmodifiable copy of the request, or returns it as is if it is already unmodifiable.
     *
     * @return the unmodifiable version of this request.
     */
    HttpRequest toUnmodifiable();

    /**
     * Create a builder.
     *
     * @return the created builder.
     */
    static ModifiableHttpRequest create() {
        return new ModifiableHttpRequestImpl();
    }
}
