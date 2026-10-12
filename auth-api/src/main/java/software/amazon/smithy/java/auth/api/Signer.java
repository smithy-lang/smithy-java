/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.auth.api;

import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.context.Context;

/**
 * Signs the given request using the given identity and returns a signed request.
 *
 * @param <RequestT>  Request to sign.
 * @param <IdentityT> Identity used when signing.
 */
@FunctionalInterface
public interface Signer<RequestT, IdentityT extends Identity> extends AutoCloseable {
    /**
     * Sign the given request.
     *
     * <p>Implementations must treat the properties as read-only because the context may be unmodifiable or shared
     * with other components of the call. Use {@link Context#modifiableCopy(Context)} before adding or overriding
     * a property.
     *
     * @param request    Request to sign.
     * @param identity  Identity used to sign the request.
     * @param properties Signing properties.
     * @return the signed request.
     */
    SignResult<RequestT> sign(RequestT request, IdentityT identity, Context properties);

    @SuppressWarnings("unchecked")
    static <RequestT, IdentityT extends Identity> Signer<RequestT, IdentityT> nullSigner() {
        return (Signer<RequestT, IdentityT>) NullSigner.INSTANCE;
    }

    @Override
    default void close() {}
}
