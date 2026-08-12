/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.dynamicclient.DynamicClient;

/**
 * Resolver that calls STS AssumeRole using source credentials resolved from
 * a source_profile chain or credential_source.
 */
final class StsAssumeRoleResolver implements IdentityResolver<AwsCredentialsIdentity>, AutoCloseable {

    private final AwsConfigCredentialSource.AssumeRole source;
    private final StsEndpointConfig endpoint;
    private final IdentityResolver<AwsCredentialsIdentity> sourceResolver;

    StsAssumeRoleResolver(
            AwsConfigCredentialSource.AssumeRole source,
            StsEndpointConfig endpoint,
            IdentityResolver<AwsCredentialsIdentity> sourceResolver
    ) {
        this.source = source;
        this.endpoint = endpoint;
        this.sourceResolver = sourceResolver;
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }

    // Visible for testing: the STS endpoint configuration this resolver was assembled with.
    StsEndpointConfig endpoint() {
        return endpoint;
    }

    @Override
    public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
        var sourceResolver = sourceResolver();
        AwsCredentialsIdentity sourceCredentials = sourceResolver.resolveIdentity(ctx).unwrap();
        return callAssumeRole(sourceResolver, sourceCredentials, source.roleArn(), source.externalId());
    }

    IdentityResolver<AwsCredentialsIdentity> sourceResolver() {
        return sourceResolver;
    }

    @Override
    public void close() {
        if (sourceResolver instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception error) {
                throw new IllegalStateException("Failed to close source credential resolver", error);
            }
        }
    }

    private IdentityResult<AwsCredentialsIdentity> callAssumeRole(
            IdentityResolver<AwsCredentialsIdentity> retainedSourceResolver,
            AwsCredentialsIdentity sourceCredentials,
            String roleArn,
            String externalId
    ) {
        var sourceResolver = createSourceResolver(sourceCredentials, retainedSourceResolver);

        try (DynamicClient client = StsClientFactory.create(sourceResolver, endpoint)) {
            // ExternalId is optional; Map.of rejects null values, so only include it when present.
            Map<String, Object> input = new HashMap<>();
            input.put("RoleArn", roleArn);
            input.put("RoleSessionName", "smithy-java-" + System.currentTimeMillis());
            if (externalId != null) {
                input.put("ExternalId", externalId);
            }
            return StsWebIdentityResolver.parseCredentials(client.call("AssumeRole", input));
        }
    }

    static IdentityResolver<AwsCredentialsIdentity> createSourceResolver(
            AwsCredentialsIdentity creds,
            IdentityResolver<AwsCredentialsIdentity> retainedSourceResolver
    ) {
        IdentityResult<AwsCredentialsIdentity> sourceResult = IdentityResult.of(creds);
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context c) {
                return sourceResult;
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }

            @Override
            public void invalidate(AwsCredentialsIdentity rejectedIdentity) {
                retainedSourceResolver.invalidate(rejectedIdentity);
            }
        };
    }
}
