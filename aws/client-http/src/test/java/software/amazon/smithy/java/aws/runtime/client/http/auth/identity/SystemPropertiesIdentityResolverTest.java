/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.runtime.client.http.auth.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

public class SystemPropertiesIdentityResolverTest {
    @Test
    void resolverReturnsExpectedIdentity() throws ExecutionException, InterruptedException {
        var resolver = new SystemPropertiesIdentityResolver();
        var value = resolver.resolveIdentity(AuthProperties.empty()).get();
        var expected = AwsCredentialsIdentity.create(
            "property_access_key",
            "property_secret_key",
            "property_token"
        );
        assertEquals(expected, value);
    }
}
