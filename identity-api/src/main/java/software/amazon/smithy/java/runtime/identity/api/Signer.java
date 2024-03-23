/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.identity.api;

import java.time.Clock;
import software.amazon.smithy.java.runtime.context.Constant;
import software.amazon.smithy.java.runtime.context.ReadableContext;

/**
 * Signs the given request using the given identity and returns a signed request.
 *
 * @param <RequestT>  Request to sign.
 * @param <IdentityT> Identity used when signing.
 */
@FunctionalInterface
public interface Signer<RequestT, IdentityT extends Identity> {
    /**
     * A {@link Clock} to be used to derive the signing time. This property defaults to the system clock.
     *
     * <p>Note, signing time may not be relevant to some signers.
     */
    Constant<Clock> SIGNING_CLOCK = new Constant<>(Clock.class, "SigningClock");

    /**
     * Sign the given request.
     *
     * @param request   Request to sign.
     * @param identityT Identity used to sign the request.
     * @param context   Signing context.
     * @return the signed request.
     * @throws UnsupportedOperationException if the given identity is incompatible with the signer.
     */
    RequestT sign(RequestT request, IdentityT identityT, ReadableContext context);
}
