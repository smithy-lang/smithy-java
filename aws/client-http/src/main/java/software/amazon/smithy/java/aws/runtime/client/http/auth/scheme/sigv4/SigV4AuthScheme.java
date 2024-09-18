/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import java.time.Clock;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.AuthProperty;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.auth.api.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides the AWS Signature Version 4 (SigV4) auth Scheme for HTTP requests.
 *
 * <p>SigV4 is the AWS signing protocol for adding authentication information to AWS API requests. The scheme uses
 * provided {@link AwsCredentialsIdentity} to sign the provided request. To use this auth
 * scheme, either add an initialized instance to your client builder or apply the sigv4 auth scheme to your service
 * in your Smithy model as follows:
 * <pre>{@code
 * use aws.auth#sigv4
 *
 * @sigv4(name: "service")
 * service MyService
 * }</pre>
 *
 * <p><strong>Note:</strong> The SigV4 auth scheme factory must be accessible on the client code generator classpath for
 * it to be added to any generated clients.
 *
 * @see <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_aws-signing.html">SigV4 Request Signing</a>
 */
public final class SigV4AuthScheme implements AuthScheme<SmithyHttpRequest, AwsCredentialsIdentity> {
    private final String signingName;

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

    public SigV4AuthScheme(String signingName) {
        this.signingName = signingName;
    }

    @Override
    public ShapeId schemeId() {
        return SigV4Trait.ID;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<AwsCredentialsIdentity> identityClass() {
        return AwsCredentialsIdentity.class;
    }

    @Override
    public AuthProperties getSignerProperties(Context context) {
        var builder = AuthProperties.builder()
            .put(SERVICE, signingName)
            .put(REGION, context.expect(SigV4Properties.REGION));
        var clock = context.get(SigV4Properties.CLOCK);
        if (clock != null) {
            builder.put(CLOCK, clock);
        }
        return builder.build();
    }

    @Override
    public Signer<SmithyHttpRequest, AwsCredentialsIdentity> signer() {
        return SigV4Signer.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<SigV4Trait> {

        @Override
        public ShapeId schemeId() {
            return SigV4Trait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(SigV4Trait trait) {
            return new SigV4AuthScheme(trait.getName());
        }
    }
}
