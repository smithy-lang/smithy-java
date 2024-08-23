/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.time.Clock;
import software.amazon.smithy.java.runtime.auth.api.AuthProperty;

/**
 * Signing properties used for SigV4 signing.
 */
public final class Sigv4Properties {
    private Sigv4Properties() {}

    /**
     * Region to use for signing. For example {@code us-east-2}.
     */
    public static final AuthProperty<String> REGION = AuthProperty.of("signingRegion");

    /**
     * Service name to use for signing. For example {@code lambda}.
     */
    public static final AuthProperty<String> SERVICE = AuthProperty.of("signingName");

    /**
     * Optional override of the clock to use for signing. If no override is provided, then the
     * default system UTC clock is used.
     */
    public static final AuthProperty<Clock> CLOCK = AuthProperty.of("signingClock");
}
