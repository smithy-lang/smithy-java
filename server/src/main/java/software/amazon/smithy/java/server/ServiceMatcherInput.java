/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.Objects;

public final class ServiceMatcherInput {

    private final String requestPath;

    ServiceMatcherInput(String requestPath) {
        this.requestPath = requestPath;
    }

    /**
     * Path of the request after removing protocol specific behaviour.
     *
     * @return
     */
    public String getRequestPath() {
        return requestPath;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ServiceMatcherInput) obj;
        return Objects.equals(this.requestPath, that.requestPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestPath);
    }

    @Override
    public String toString() {
        return "ServiceMatcherInput[" +
            "requestPath=" + requestPath + ']';
    }

}
