/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

import java.net.URI;
import software.amazon.smithy.java.io.uri.SmithyUri;

/**
 * A modifiable HTTP request.
 */
public interface ModifiableHttpRequest extends ModifiableHttpMessage, HttpRequest {
    /**
     * Set the request method.
     *
     * @param method Method to set.
     */
    void setMethod(String method);

    /**
     * Set the request URI.
     *
     * @param uri SmithyUri to set.
     */
    void setUri(SmithyUri uri);

    /**
     * Set the request URI.
     *
     * @param uri URI to set.
     */
    default void setUri(URI uri) {
        setUri(SmithyUri.of(uri));
    }

    @Override
    default ModifiableHttpRequest toModifiable() {
        return this;
    }
}
