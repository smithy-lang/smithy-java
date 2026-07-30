/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;

class StsWebIdentityResolverTest {

    private static final StsEndpointConfig TEST_ENDPOINT = new StsEndpointConfig("us-east-1", false);

    @Test
    void failsWhenTokenFileDoesNotExist() {
        var source = new AwsConfigCredentialSource.WebIdentityToken(
                "arn:aws:iam::123:role/R",
                "/nonexistent/path/token",
                "session",
                null);
        var resolver = new StsWebIdentityResolver(source, StsClientFactory.createNoAuth(TEST_ENDPOINT));

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
        var resolver = new StsWebIdentityResolver(source, StsClientFactory.createNoAuth(TEST_ENDPOINT));

        // Will fail at the HTTP call (no real STS endpoint), but verifies token was read
        // and the call was attempted with correct parameters
        var ex = assertThrows(RuntimeException.class, () -> resolver.resolveIdentity(Context.create()));
        assertTrue(ex.getMessage().contains("Failed to assume role with web identity"));
        assertTrue(ex.getMessage().contains("arn:aws:iam::123:role/R"));
    }

    @Test
    void reloadsTokenFileAndRetriesExpiredTokenOnce(@TempDir Path tmp) throws IOException {
        Path tokenFile = tmp.resolve("token");
        Files.writeString(tokenFile, "expired-token");
        var source = new AwsConfigCredentialSource.WebIdentityToken(
                "arn:aws:iam::123:role/R",
                tokenFile.toString(),
                "session",
                null);
        var calls = new AtomicInteger();
        var tokens = new ArrayList<String>();
        var resolver = new StsWebIdentityResolver(source, input -> {
            tokens.add((String) input.get("WebIdentityToken"));
            if (calls.getAndIncrement() == 0) {
                try {
                    Files.writeString(tokenFile, "fresh-token");
                } catch (IOException error) {
                    throw new UncheckedIOException(error);
                }
                throw new TestModeledException("ExpiredTokenException");
            }
            return credentialResponse();
        });

        var result = resolver.resolveIdentity(Context.empty());

        assertEquals("AKID", result.identity().accessKeyId());
        assertEquals(2, calls.get());
        assertEquals(List.of("expired-token", "fresh-token"), tokens);
    }

    private static Document credentialResponse() {
        return Document.ofObject(Map.of(
                "Credentials",
                Map.of(
                        "AccessKeyId",
                        "AKID",
                        "SecretAccessKey",
                        "SECRET",
                        "SessionToken",
                        "TOKEN",
                        "Expiration",
                        Instant.now().plusSeconds(3600).toString())));
    }

    private static final class TestModeledException extends ModeledException {
        private TestModeledException(String name) {
            super(Schema.structureBuilder(
                    ShapeId.from("com.amazonaws.sts#" + name),
                    new ErrorTrait("client")).build(),
                    name);
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {}

        @Override
        public <T> T getMemberValue(Schema member) {
            return null;
        }
    }
}
