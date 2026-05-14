/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.sts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;

class StsResponseParsingTest {

    @Test
    void parseCredentialsExtractsAllFields() {
        var creds = Document.of(Map.of(
                "AccessKeyId",
                Document.of("AKID"),
                "SecretAccessKey",
                Document.of("SECRET"),
                "SessionToken",
                Document.of("TOKEN"),
                "Expiration",
                Document.of("2099-01-01T00:00:00Z")));
        var assumedRoleUser = Document.of(Map.of(
                "AssumedRoleId",
                Document.of("role-id"),
                "Arn",
                Document.of("arn:aws:sts::123456789012:assumed-role/RoleA/session")));
        var output = Document.of(Map.of(
                "Credentials",
                creds,
                "AssumedRoleUser",
                assumedRoleUser));

        var result = StsWebIdentityResolver.parseCredentials(output);
        var id = result.unwrap();
        assertEquals("AKID", id.accessKeyId());
        assertEquals("SECRET", id.secretAccessKey());
        assertEquals("TOKEN", id.sessionToken());
        assertEquals("123456789012", id.accountId());
        assertNotNull(id.expirationTime());
    }

    @Test
    void parseCredentialsHandlesMissingAssumedRoleUser() {
        var creds = Document.of(Map.of(
                "AccessKeyId",
                Document.of("AK"),
                "SecretAccessKey",
                Document.of("SK"),
                "SessionToken",
                Document.of("TOK"),
                "Expiration",
                Document.of("2099-06-01T00:00:00Z")));
        var output = Document.of(Map.of("Credentials", creds));

        var result = StsWebIdentityResolver.parseCredentials(output);
        var id = result.unwrap();
        assertEquals("AK", id.accessKeyId());
        assertEquals("SK", id.secretAccessKey());
        assertEquals("TOK", id.sessionToken());
        assertNull(id.accountId());
    }
}
