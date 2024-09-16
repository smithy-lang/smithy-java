/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.scheme.sigv4;

import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.java.aws.runtime.client.http.AwsClientConfigProperties;
import software.amazon.smithy.java.aws.runtime.client.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
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
        // TODO: eventually the Signer should be updated to not depend on any "AWS-client" concepts.
        var builder = AuthProperties.builder()
            .put(SigV4Properties.SERVICE, signingName)
            .put(SigV4Properties.REGION, context.expect(AwsClientConfigProperties.REGION));
        var clock = context.get(AwsClientConfigProperties.CLOCK);
        if (clock != null) {
            builder.put(SigV4Properties.CLOCK, clock);
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
