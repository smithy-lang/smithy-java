/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.runtime.http.api.ModifiableHttpHeaders;

public final class HttpResponse extends ResponseImpl {

    private int statusCode;

    public HttpResponse(ModifiableHttpHeaders headers) {
        this.headers = headers;
    }

    private final ModifiableHttpHeaders headers;

    public ModifiableHttpHeaders headers() {
        return headers;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }
}
