/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;

import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * A signer that does nothing.
 */
@SuppressWarnings("rawtypes")
final class NoopSigner implements Signer {

    /**
     * An instance of NoopSigner.
     */
    public static final NoopSigner INSTANCE = new NoopSigner();

    private NoopSigner() {}

    /**
     * Sign the given request, by doing nothing.
     *
     * @param request    Request to sign.
     * @param identity   Identity used to sign the request. This is unused.
     * @param properties Signing properties. This is unused.
     * @return the request as-is.
     */
    @Override
    public Object sign(Object request, Identity identity, AuthProperties properties) {
        return request;
    }
}
