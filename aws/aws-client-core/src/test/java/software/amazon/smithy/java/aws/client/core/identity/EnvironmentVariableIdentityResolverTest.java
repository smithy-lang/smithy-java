/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.AuthProperties;

public class EnvironmentVariableIdentityResolverTest {

    @Test
    void resolverReturnsExpectedIdentity() throws ExecutionException, InterruptedException {
        var resolver = new EnvironmentVariableIdentityResolver();
        var value = resolver.resolveIdentity(AuthProperties.empty()).get();
        var expected = AwsCredentialsIdentity.create(
                "env_access_key",
                "env_secret_key",
                "env_token");

        assertEquals(expected, value.unwrap());
    }
}
