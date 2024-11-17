/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.api;

/**
 * A modifiable HTTP response.
 */
public interface SmithyModifiableHttpResponse extends SmithyModifiableHttpMessage, SmithyHttpResponse {
    /**
     * Set the status code.
     *
     * @param statusCode Status code to set.
     */
    void setStatusCode(int statusCode);

    @Override
    default SmithyModifiableHttpResponse toModifiable() {
        return this;
    }
}
