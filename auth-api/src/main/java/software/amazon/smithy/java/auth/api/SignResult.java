/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api;

/**
 * Holds the result of signing a request of type {@link RequestT}.
 *
 * @param signedRequest the signed request
 * @param signature     the signature
 * @param <RequestT>    the type of the request
 */
public record SignResult<RequestT>(
        RequestT signedRequest,
        String signature) {

    /**
     * Creates a sign result with an empty string
     *
     * @param signedRequest the signed request
     */
    public SignResult(RequestT signedRequest) {
        this(signedRequest, "");
    }
}
