/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

/**
 * A modifiable HTTP response.
 */
public interface ModifiableHttpResponse extends ModifiableHttpMessage, HttpResponse {
    /**
     * Set the status code.
     *
     * @param statusCode Status code to set.
     */
    void setStatusCode(int statusCode);

    @Override
    default ModifiableHttpResponse toModifiable() {
        return this;
    }
}
