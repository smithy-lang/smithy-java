/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.http.auth.scheme.sigv4;

import java.time.Instant;
import software.amazon.smithy.java.runtime.auth.api.AuthProperty;

/**
 * Signing properties used for SigV4 signing.
 */
public final class Sigv4Properties {
    private Sigv4Properties() {}

    public static final AuthProperty<String> REGION = AuthProperty.of("signingRegion");
    public static final AuthProperty<String> SERVICE = AuthProperty.of("signingName");

    // TODO: Remove once the request has a timestamp?
    // This property is used to set a static time for testing the signing process.
    static final AuthProperty<Instant> TIMESTAMP = AuthProperty.of("signingTimestamp");
}
