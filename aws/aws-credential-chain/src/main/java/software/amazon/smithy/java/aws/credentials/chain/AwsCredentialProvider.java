/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.List;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

/**
 * SPI for registering a credential provider into the AWS default credential chain.
 */
public interface AwsCredentialProvider {
    /**
     * @return the unique name of this provider (for example {@code "environment"}, {@code "profile"}, {@code "imds"}).
     */
    String name();

    /**
     * @return alternative names for the provider.
     */
    default List<String> aliases() {
        return List.of();
    }

    /**
     * @return the ordering constraint for this provider.
     */
    OrderingConstraint ordering();

    /**
     * Create the credential resolver for this provider.
     *
     * <p>Called once during chain assembly. The returned resolver is used for the lifetime of the chain.
     *
     * @param context shared resources provided by the chain.
     * @return the resolver.
     */
    IdentityResolver<AwsCredentialsIdentity> create(ProviderContext context);
}
