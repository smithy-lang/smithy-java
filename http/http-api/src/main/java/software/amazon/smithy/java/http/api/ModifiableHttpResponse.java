/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.api;

/**
 * A modifiable HTTP response.
 */
public interface ModifiableHttpResponse extends ModifiableHttpMessage<ModifiableHttpResponse>, HttpResponse {
    /**
     * Set the status code.
     *
     * @param statusCode Status code to set.
     * @return this response.
     */
    ModifiableHttpResponse setStatusCode(int statusCode);

    @Override
    default ModifiableHttpResponse toModifiable() {
        return this;
    }
}
