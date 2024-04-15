/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api;

import java.time.Clock;
import java.util.Objects;

/**
 * An object identity-based key used to add properties to {@link AuthProperties}.
 *
 * @param <T> Value type associated with the property.
 */
public final class AuthProperty<T> {

    /**
     * A {@link Clock} to be used to derive the signing time. This property defaults to the system clock.
     *
     * <p>Note, signing time may not be relevant to some signers.
     */
    public static final AuthProperty<Clock> SIGNING_CLOCK = of("SigningClock");

    private final String name;

    private AuthProperty(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Create a new object identity-based AuthProperty.
     *
     * @param name Name of the value used to describe the property.
     * @return the created property.
     * @param <T> Value type associated with the property.
     */
    public static <T> AuthProperty<T> of(String name) {
        return new AuthProperty<>(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
