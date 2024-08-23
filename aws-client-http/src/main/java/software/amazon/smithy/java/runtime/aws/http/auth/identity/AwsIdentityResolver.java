/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.http.auth.identity;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;

// TODO: Use an actual resolver that respects credential chain
public class AwsIdentityResolver implements IdentityResolver<AwsCredentialsIdentity> {

    private static final AwsCredentialsIdentity EXAMPLE_IDENTITY = AwsCredentialsIdentity.create(
        "AKIAIOSFODNN7EXAMPLE",
        "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    );

    @Override
    public CompletableFuture<AwsCredentialsIdentity> resolveIdentity(AuthProperties requestProperties) {
        return CompletableFuture.supplyAsync(() -> EXAMPLE_IDENTITY);
    }

    @Override
    public Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }
}
