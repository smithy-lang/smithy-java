/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * A signer that does nothing.
 */
@SuppressWarnings("rawtypes")
final class NullSigner implements Signer {

    /**
     * An instance of NoopSigner.
     */
    public static final NullSigner INSTANCE = new NullSigner();

    private NullSigner() {}

    /**
     * Sign the given request, by doing nothing.
     *
     * @param request    Request to sign.
     * @param identity   Identity used to sign the request. This is unused.
     * @param properties Signing properties. This is unused.
     * @return the request as-is.
     */
    @Override
    public CompletableFuture<Object> sign(Object request, Identity identity, AuthProperties properties) {
        return CompletableFuture.completedFuture(request);
    }
}
