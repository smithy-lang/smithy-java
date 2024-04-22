/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;

import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * Signs the given request using the given identity and returns a signed request.
 *
 * @param <RequestT>  Request to sign.
 * @param <IdentityT> Identity used when signing.
 */
@FunctionalInterface
public interface Signer<RequestT, IdentityT extends Identity> {

    /**
     * Sign the given request.
     *
     * @param request    Request to sign.
     * @param identity  Identity used to sign the request.
     * @param properties Signing properties.
     * @return the signed request.
     */
    RequestT sign(RequestT request, IdentityT identity, AuthProperties properties);

    @SuppressWarnings("unchecked")
    static <RequestT, IdentityT extends Identity> Signer<RequestT, IdentityT> nullSigner() {
        return (Signer<RequestT, IdentityT>) NullSigner.INSTANCE;
    }
}
