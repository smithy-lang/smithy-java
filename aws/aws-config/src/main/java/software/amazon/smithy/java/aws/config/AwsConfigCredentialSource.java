/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.Map;
import java.util.Objects;

/**
 * AWS Config file credential types.
 *
 * <p>A {@link AwsProfile} exposes an ordered list of these sources (via {@link AwsProfile#credentialSources()})
 * computed from its raw properties. The order follows the AWS SDK shared-configuration specification's priority for
 * credential-type selection, highest first. A profile that defines nothing credential-related will produce an empty
 * list; most profiles produce exactly one source.
 */
public sealed interface AwsConfigCredentialSource {
    /**
     * Long-term credentials from {@code aws_access_key_id} + {@code aws_secret_access_key}. Used
     * when no higher-priority source applies.
     *
     * @param accessKeyId the AWS access key ID.
     * @param secretAccessKey the AWS secret access key.
     * @param accountId the AWS account ID, or {@code null} if not specified.
     */
    record StaticKeys(String accessKeyId, String secretAccessKey, String accountId)
            implements AwsConfigCredentialSource {
        public StaticKeys {
            Objects.requireNonNull(accessKeyId, "accessKeyId");
            Objects.requireNonNull(secretAccessKey, "secretAccessKey");
        }
    }

    /**
     * Temporary credentials from {@code aws_access_key_id} + {@code aws_secret_access_key} +
     * {@code aws_session_token}. Distinguished from {@link StaticKeys} by the presence of the
     * session token.
     *
     * @param accessKeyId the AWS access key ID.
     * @param secretAccessKey the AWS secret access key.
     * @param sessionToken the AWS session token.
     * @param accountId the AWS account ID, or {@code null} if not specified.
     */
    record SessionKeys(String accessKeyId, String secretAccessKey, String sessionToken, String accountId)
            implements AwsConfigCredentialSource {
        public SessionKeys {
            Objects.requireNonNull(accessKeyId, "accessKeyId");
            Objects.requireNonNull(secretAccessKey, "secretAccessKey");
            Objects.requireNonNull(sessionToken, "sessionToken");
        }
    }

    /**
     * Role assumption via {@code sts:AssumeRole}.
     *
     * @param roleArn the ARN of the role to assume.
     * @param sourceProfile the name of the profile providing base credentials, or {@code null}.
     * @param credentialSource the named credential source for base credentials, or {@code null}.
     * @param externalId the external ID for the role assumption, or {@code null}.
     * @param roleSessionName the session name for the assumed role, or {@code null}.
     * @param mfaSerial the MFA device serial number, or {@code null}.
     * @param durationSeconds the duration of the role session in seconds, or {@code null}.
     * @param region the region to use for the STS call, or {@code null}.
     */
    record AssumeRole(
            String roleArn,
            String sourceProfile,
            String credentialSource,
            String externalId,
            String roleSessionName,
            String mfaSerial,
            Integer durationSeconds,
            String region) implements AwsConfigCredentialSource {
        public AssumeRole {
            Objects.requireNonNull(roleArn, "roleArn");
        }
    }

    /**
     * Role assumption via {@code sts:AssumeRoleWithWebIdentity}.
     *
     * @param roleArn the ARN of the role to assume.
     * @param webIdentityTokenFile the path to the file containing the web identity token.
     * @param roleSessionName the session name for the assumed role, or {@code null}.
     * @param region the region to use for the STS call, or {@code null}.
     */
    record WebIdentityToken(
            String roleArn,
            String webIdentityTokenFile,
            String roleSessionName,
            String region) implements AwsConfigCredentialSource {
        public WebIdentityToken {
            Objects.requireNonNull(roleArn, "roleArn");
            Objects.requireNonNull(webIdentityTokenFile, "webIdentityTokenFile");
        }
    }

    /**
     * SSO-derived credentials via a named {@code [sso-session NAME]} section.
     *
     * @param sessionName the name of the {@code [sso-session]} section to use.
     * @param accountId the AWS account ID to request credentials for.
     * @param roleName the SSO role name to assume.
     */
    record SsoSession(String sessionName, String accountId, String roleName) implements AwsConfigCredentialSource {
        public SsoSession {
            Objects.requireNonNull(sessionName, "sessionName");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(roleName, "roleName");
        }

        static SsoSession fromProperties(Map<String, String> p) {
            String session = p.get("sso_session");
            String account = p.get("sso_account_id");
            String role = p.get("sso_role_name");
            if (session == null || session.isEmpty()
                    || account == null
                    || account.isEmpty()
                    || role == null
                    || role.isEmpty()) {
                return null;
            }
            return new SsoSession(session, account, role);
        }
    }

    /**
     * Legacy (pre-{@code sso-session}) SSO form where the start URL and region are inlined
     * directly in the profile.
     *
     * @param startUrl the SSO start URL.
     * @param region the SSO region.
     * @param accountId the AWS account ID to request credentials for.
     * @param roleName the SSO role name to assume.
     */
    record LegacySso(String startUrl, String region, String accountId, String roleName)
            implements AwsConfigCredentialSource {
        public LegacySso {
            Objects.requireNonNull(startUrl, "startUrl");
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(roleName, "roleName");
        }

        static LegacySso fromProperties(Map<String, String> p) {
            String url = p.get("sso_start_url");
            String region = p.get("sso_region");
            String account = p.get("sso_account_id");
            String role = p.get("sso_role_name");
            if (url == null || url.isEmpty()
                    || region == null
                    || region.isEmpty()
                    || account == null
                    || account.isEmpty()
                    || role == null
                    || role.isEmpty()) {
                return null;
            }
            return new LegacySso(url, region, account, role);
        }
    }

    /**
     * Credentials produced by invoking an external program configured by {@code credential_process}.
     *
     * @param commandLine the command to execute.
     */
    record CredentialProcess(String commandLine) implements AwsConfigCredentialSource {
        public CredentialProcess {
            Objects.requireNonNull(commandLine, "commandLine");
        }

        static CredentialProcess fromProperties(Map<String, String> p) {
            String cmd = p.get("credential_process");
            if (cmd == null || cmd.isEmpty()) {
                return null;
            }
            return new CredentialProcess(cmd);
        }
    }

    /**
     * Credentials from an AWS Sign-In login session, configured via {@code login_session}.
     *
     * @param loginSession the login session identifier (typically an IAM user ARN).
     */
    record LoginSession(String loginSession) implements AwsConfigCredentialSource {
        public LoginSession {
            Objects.requireNonNull(loginSession, "loginSession");
        }

        static LoginSession fromProperties(Map<String, String> p) {
            String session = p.get("login_session");
            if (session == null || session.isEmpty()) {
                return null;
            }
            return new LoginSession(session);
        }
    }
}
