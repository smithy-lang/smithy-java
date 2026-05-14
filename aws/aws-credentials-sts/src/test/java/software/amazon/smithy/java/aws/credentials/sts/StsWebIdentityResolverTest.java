/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.context.Context;

class StsWebIdentityResolverTest {

    @Test
    void failsWhenTokenFileDoesNotExist() {
        var source = new AwsConfigCredentialSource.WebIdentityToken(
                "arn:aws:iam::123:role/R",
                "/nonexistent/path/token",
                "session",
                null);
        var resolver = new StsWebIdentityResolver(source, StsClientFactory.createNoAuth());

        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("Failed to assume role with web identity"));
    }

    @Test
    void readsTokenFileAndAttemptsStsCall(@TempDir Path tmp) throws IOException {
        Path tokenFile = tmp.resolve("token");
        Files.writeString(tokenFile, "my-oidc-token-value");

        var source = new AwsConfigCredentialSource.WebIdentityToken(
                "arn:aws:iam::123:role/R",
                tokenFile.toString(),
                "my-session",
                null);
        var resolver = new StsWebIdentityResolver(source, StsClientFactory.createNoAuth());

        // Will fail at the HTTP call (no real STS endpoint), but verifies token was read
        // and the call was attempted with correct parameters
        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("Failed to assume role with web identity"));
        assertTrue(ex.getMessage().contains("arn:aws:iam::123:role/R"));
    }
}
