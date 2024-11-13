/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.auth.identity;

import java.util.Objects;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;

/**
 * The result of attempting to resolve an identity.
 *
 * @param <IdentityT> identity to resolve.
 */
public final class IdentityResult<IdentityT extends Identity> {

    private final Class<?> resolver;
    private final IdentityT identity;
    private final String error;

    /**
     * Create a result that captures a resolved identity.
     *
     * @param identity Identity that was found.
     * @return the created result.
     * @param <IdentityT> Kind of identity.
     */
    public static <IdentityT extends Identity> IdentityResult<IdentityT> of(IdentityT identity) {
        return new IdentityResult<>(identity);
    }

    /**
     * Create a result that captures when an identity cannot be found.
     *
     * <p>This is used for expected failures like missing environment variables or files. It is not meant to be used
     * for unexpected failures, like malformed input, malformed files, network errors, etc.
     *
     * @param resolver The resolver class that was unable to resolve credentials.
     * @param error The error message describing what identity could not be found and why.
     * @return the created result.
     * @param <IdentityT> Kind of identity.
     */
    public static <IdentityT extends Identity> IdentityResult<IdentityT> ofError(Class<?> resolver, String error) {
        return new IdentityResult<>(resolver, error);
    }

    private IdentityResult(IdentityT identity) {
        this.identity = identity;
        this.error = null;
        this.resolver = null;
    }

    private IdentityResult(Class<?> resolver, String error) {
        this.identity = null;
        this.error = error;
        this.resolver = resolver;
    }

    /**
     * Get the resolved identity, or null if not found.
     *
     * @return the resolved identity.
     */
    public IdentityT identity() {
        return identity;
    }

    /**
     * Get the resolved identity, or throw an {@link IdentityNotFoundException} if no identity is present.
     *
     * @return the resolved identity.
     * @throws IdentityNotFoundException when the identity is not present.
     */
    public IdentityT unwrap() {
        if (identity == null) {
            throw new IdentityNotFoundException(
                "Unable to resolve an identity: " + error
                    + " (" + resolver.getName() + ")"
            );
        } else {
            return identity;
        }
    }

    /**
     * Get the error message for why the identity can be found.
     *
     * @return the error message, or null if the identity is present.
     */
    public String error() {
        return error;
    }

    /**
     * Get the identity resolver class that could not resolve an identity.
     *
     * @return the identity resolver.
     */
    public Class<?> resolver() {
        return resolver;
    }

    @Override
    public String toString() {
        if (identity != null) {
            return "IdentityResult[identity=" + identity.getClass().getName() + ']';
        } else {
            return "IdentityResult[error='" + error + "', resolver=" + resolver.getName() + ']';
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdentityResult<?> that)) {
            return false;
        }
        return Objects.equals(identity, that.identity) && Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, error);
    }
}
