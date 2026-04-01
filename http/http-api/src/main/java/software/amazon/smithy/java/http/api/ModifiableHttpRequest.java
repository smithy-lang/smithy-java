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
public interface ModifiableHttpRequest extends ModifiableHttpMessage<ModifiableHttpRequest>, HttpRequest {
    /**
     * Set the request method.
     *
     * @param method Method to set.
     * @return this request.
     */
    ModifiableHttpRequest setMethod(String method);

    /**
     * Set the request URI.
     *
     * @param uri SmithyUri to set.
     * @return this request.
     */
    ModifiableHttpRequest setUri(SmithyUri uri);

    /**
     * Set the request URI.
     *
     * @param uri URI to set.
     * @return this request.
     */
    default ModifiableHttpRequest setUri(URI uri) {
        return setUri(SmithyUri.of(uri));
    }

    @Override
    default ModifiableHttpRequest toModifiable() {
        return this;
    }
}
