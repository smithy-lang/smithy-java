/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.context.Context;

public class SystemPropertiesIdentityResolverTest {
    @Test
    void resolverReturnsExpectedIdentity() {
        var resolver = new SystemPropertiesIdentityResolver();
        var value = resolver.resolveIdentity(Context.empty());
        var expected = AwsCredentialsIdentity.create(
                "property_access_key",
                "property_secret_key",
                "property_token");

        assertEquals(expected, value.unwrap());
    }
}
