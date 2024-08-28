/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

<<<<<<< HEAD:aws/client-http/src/main/java/software/amazon/smithy/java/aws/runtime/client/http/auth/identity/AwsCredentialsResolver.java
package software.amazon.smithy.java.aws.runtime.client.http.auth.identity;

import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;

/**
 * An {@link IdentityResolver} that resolves a {@link AwsCredentialsIdentity} for authentication.
 */
=======
package software.amazon.smithy.java.runtime.aws.http.auth.identity;

import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;

>>>>>>> 95767d49 (Add default resolvers):aws-client-http/src/main/java/software/amazon/smithy/java/runtime/aws/http/auth/identity/AwsCredentialsResolver.java
interface AwsCredentialsResolver extends IdentityResolver<AwsCredentialsIdentity> {
    @Override
    default Class<AwsCredentialsIdentity> identityType() {
        return AwsCredentialsIdentity.class;
    }
}
