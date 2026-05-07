/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsProfileCredentialSourcesTest {

    @Test
    void staticKeysOnly() {
        AwsProfile p = profileFromContent("""
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);
        List<AwsConfigCredentialSource> sources = p.credentialSources();
        assertEquals(1, sources.size());
        AwsConfigCredentialSource.StaticKeys s =
                assertInstanceOf(AwsConfigCredentialSource.StaticKeys.class, sources.get(0));
        assertEquals("AK", s.accessKeyId());
        assertEquals("SK", s.secretAccessKey());
        assertNull(s.accountId());
    }

    @Test
    void sessionKeysWhenSessionTokenPresent() {
        AwsProfile p = profileFromContent("""
                [default]
                aws_access_key_id = AK
                aws_secret_access_key = SK
                aws_session_token = ST
                aws_account_id = 111111111111
                """);
        AwsConfigCredentialSource source = p.credentialSources().get(0);
        AwsConfigCredentialSource.SessionKeys s = assertInstanceOf(AwsConfigCredentialSource.SessionKeys.class, source);
        assertEquals("AK", s.accessKeyId());
        assertEquals("SK", s.secretAccessKey());
        assertEquals("ST", s.sessionToken());
        assertEquals("111111111111", s.accountId());
    }

    @Test
    void roleArnIsFirstButStaticKeysAlsoReturned() {
        AwsProfile p = profileFromContent("""
                [default]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                aws_access_key_id = FALLBACK_AK
                aws_secret_access_key = FALLBACK_SK
                """);
        List<AwsConfigCredentialSource> sources = p.credentialSources();
        assertEquals(2, sources.size());
        AwsConfigCredentialSource.AssumeRole r =
                assertInstanceOf(AwsConfigCredentialSource.AssumeRole.class, sources.get(0));
        assertEquals("arn:aws:iam::123:role/X", r.roleArn());
        assertEquals("base", r.sourceProfile());
        AwsConfigCredentialSource.StaticKeys s =
                assertInstanceOf(AwsConfigCredentialSource.StaticKeys.class, sources.get(1));
        assertEquals("FALLBACK_AK", s.accessKeyId());
    }

    @Test
    void webIdentityWhenRoleArnAndTokenFilePresent() {
        AwsProfile p = profileFromContent("""
                [default]
                role_arn = arn:aws:iam::123:role/X
                web_identity_token_file = /tmp/oidc-token
                role_session_name = sess
                """);
        AwsConfigCredentialSource source = p.credentialSources().get(0);
        AwsConfigCredentialSource.WebIdentityToken w =
                assertInstanceOf(AwsConfigCredentialSource.WebIdentityToken.class, source);
        assertEquals("arn:aws:iam::123:role/X", w.roleArn());
        assertEquals("/tmp/oidc-token", w.webIdentityTokenFile());
        assertEquals("sess", w.roleSessionName());
    }

    @Test
    void ssoSessionFormWhenSessionNamed() {
        AwsProfile p = profileFromContent("""
                [default]
                sso_session = my-sess
                sso_account_id = 111111111111
                sso_role_name = Dev
                """);
        AwsConfigCredentialSource source = p.credentialSources().get(0);
        AwsConfigCredentialSource.SsoSession s = assertInstanceOf(AwsConfigCredentialSource.SsoSession.class, source);
        assertEquals("my-sess", s.sessionName());
        assertEquals("111111111111", s.accountId());
        assertEquals("Dev", s.roleName());
    }

    @Test
    void legacySsoWhenInlineStartUrlProvided() {
        AwsProfile p = profileFromContent("""
                [default]
                sso_start_url = https://corp.awsapps.com/start
                sso_region = us-east-1
                sso_account_id = 111111111111
                sso_role_name = Dev
                """);
        AwsConfigCredentialSource source = p.credentialSources().get(0);
        AwsConfigCredentialSource.LegacySso s = assertInstanceOf(AwsConfigCredentialSource.LegacySso.class, source);
        assertEquals("https://corp.awsapps.com/start", s.startUrl());
        assertEquals("us-east-1", s.region());
    }

    @Test
    void credentialProcessWhenNoHigherPriorityForm() {
        AwsProfile p = profileFromContent("""
                [default]
                credential_process = /usr/local/bin/awscreds --env=dev
                """);
        AwsConfigCredentialSource source = p.credentialSources().get(0);
        AwsConfigCredentialSource.CredentialProcess s =
                assertInstanceOf(AwsConfigCredentialSource.CredentialProcess.class, source);
        assertEquals("/usr/local/bin/awscreds --env=dev", s.commandLine());
    }

    @Test
    void emptyListWhenNoRecognizedCredentialProperties() {
        AwsProfile p = profileFromContent("""
                [default]
                region = us-east-1
                """);
        assertTrue(p.credentialSources().isEmpty());
    }

    @Test
    void durationSecondsParsedAsInteger() {
        AwsProfile p = profileFromContent("""
                [default]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                duration_seconds = 3600
                """);
        AwsConfigCredentialSource.AssumeRole r =
                assertInstanceOf(AwsConfigCredentialSource.AssumeRole.class, p.credentialSources().get(0));
        assertEquals(3600, r.durationSeconds());
    }

    @Test
    void badDurationSecondsIsSilentlyNull() {
        AwsProfile p = profileFromContent("""
                [default]
                role_arn = arn:aws:iam::123:role/X
                source_profile = base
                duration_seconds = not-a-number
                """);
        AwsConfigCredentialSource.AssumeRole r =
                assertInstanceOf(AwsConfigCredentialSource.AssumeRole.class, p.credentialSources().get(0));
        assertNull(r.durationSeconds());
    }

    @Test
    void loginSessionDetected() {
        AwsProfile p = profileFromContent("""
                [default]
                login_session = arn:aws:iam::0123456789012:user/Admin
                """);
        List<AwsConfigCredentialSource> sources = p.credentialSources();
        assertEquals(1, sources.size());
        AwsConfigCredentialSource.LoginSession s =
                assertInstanceOf(AwsConfigCredentialSource.LoginSession.class, sources.get(0));
        assertEquals("arn:aws:iam::0123456789012:user/Admin", s.loginSession());
    }

    @Test
    void multipleSourcesReturnedInPriorityOrder() {
        AwsProfile p = profileFromContent("""
                [default]
                sso_session = corp
                sso_account_id = 111111111111
                sso_role_name = Dev
                credential_process = /usr/bin/get-creds
                aws_access_key_id = AK
                aws_secret_access_key = SK
                """);
        List<AwsConfigCredentialSource> sources = p.credentialSources();
        assertEquals(3, sources.size());
        assertInstanceOf(AwsConfigCredentialSource.SsoSession.class, sources.get(0));
        assertInstanceOf(AwsConfigCredentialSource.CredentialProcess.class, sources.get(1));
        assertInstanceOf(AwsConfigCredentialSource.StaticKeys.class, sources.get(2));
    }

    private static AwsProfile profileFromContent(String content) {
        Map<String, AwsProfile> profiles = ProfileStandardizer.standardize(
                AwsProfileFileParser.parse(content),
                AwsConfigFileType.CREDENTIALS).profiles();
        return profiles.get("default");
    }
}
