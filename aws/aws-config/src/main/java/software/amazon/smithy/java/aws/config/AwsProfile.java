/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * An AWS named profile, as parsed from {@code ~/.aws/config} or {@code ~/.aws/credentials}.
 *
 * <p>A profile has:
 * <ul>
 *   <li>A name</li>
 *   <li>A flat map of scalar properties. Property keys are stored lower-cased and compared case-insensitively</li>
 *   <li>A map of sub-properties keyed by parent property name. Sub-properties represent the nested INI form
 *   {@code parent = \n   child = value\n   other = value}</li>
 * </ul>
 */
public final class AwsProfile {

    private final String name;
    private final Map<String, String> properties;
    private final Map<String, Map<String, String>> subProperties;
    private final List<AwsConfigCredentialSource> credentialSources;

    AwsProfile(String name, Map<String, String> properties, Map<String, Map<String, String>> subProperties) {
        this.name = Objects.requireNonNull(name, "name");
        this.properties = Collections.unmodifiableMap(Objects.requireNonNull(properties, "properties"));
        this.subProperties = Collections.unmodifiableMap(Objects.requireNonNull(subProperties, "subProperties"));
        this.credentialSources = computeCredentialSources();
    }

    /**
     * @return the profile name as it appeared in the file (for example {@code "default"} or {@code "dev"}).
     */
    public String name() {
        return name;
    }

    /**
     * @return an unmodifiable, insertion-ordered map of the profile's scalar properties. Keys are
     *         lower-cased.
     */
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * @return an unmodifiable, insertion-ordered map from parent property name to its sub-properties.
     *         Parent keys are lower-cased. Most profiles will not have any sub-properties.
     */
    public Map<String, Map<String, String>> subProperties() {
        return subProperties;
    }

    /**
     * Get a single property value. Keys are compared case-insensitively.
     *
     * @param key property name.
     * @return the property value, or {@code null} if not set.
     */
    public String property(String key) {
        return properties.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Get the sub-properties for a parent property, if any. Keys are compared case-insensitively.
     *
     * @param key parent property name.
     * @return the sub-property map, or {@code null} if no sub-properties exist under that key.
     */
    public Map<String, String> subProperties(String key) {
        return subProperties.get(key.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the computed the list of credential sources described by this profile, in AWS SDK shared-configuration
     * priority order (highest priority first).
     *
     * <p>Every credential form a profile describes is returned, not only the highest priority. For example, a profile
     * that sets both {@code role_arn} and {@code aws_access_key_id} produces a two-element list with an
     * {@link AwsConfigCredentialSource.AssumeRole} first and an {@link AwsConfigCredentialSource.StaticKeys} second.
     * This lets consumers iterate the list and dispatch to the first handler that can process an entry; callers that
     * want strict priority can stop at index zero.
     *
     * <p>The mapping from raw properties to typed sources, in priority order, is:
     * <ol>
     *   <li>{@link AwsConfigCredentialSource.WebIdentityToken} when {@code role_arn} and
     *       {@code web_identity_token_file} are both set.</li>
     *   <li>{@link AwsConfigCredentialSource.AssumeRole} when {@code role_arn} is set and no
     *       {@code web_identity_token_file} is present.</li>
     *   <li>{@link AwsConfigCredentialSource.SsoSession} when {@code sso_session},
     *       {@code sso_account_id}, and {@code sso_role_name} are all set.</li>
     *   <li>{@link AwsConfigCredentialSource.LegacySso} when the inline SSO keys
     *       ({@code sso_start_url}, {@code sso_region}, {@code sso_account_id},
     *       {@code sso_role_name}) are all set.</li>
     *   <li>{@link AwsConfigCredentialSource.LoginSession} when {@code login_session} is set.</li>
     *   <li>{@link AwsConfigCredentialSource.CredentialProcess} when {@code credential_process} is set.</li>
     *   <li>{@link AwsConfigCredentialSource.SessionKeys} when {@code aws_access_key_id},
     *       {@code aws_secret_access_key}, and {@code aws_session_token} are all set.</li>
     *   <li>{@link AwsConfigCredentialSource.StaticKeys} when {@code aws_access_key_id} and
     *       {@code aws_secret_access_key} are set (and no session token).</li>
     * </ol>
     *
     * @return an immutable, priority-ordered list of typed credential sources, possibly empty.
     */
    public List<AwsConfigCredentialSource> credentialSources() {
        return credentialSources;
    }

    private List<AwsConfigCredentialSource> computeCredentialSources() {
        List<AwsConfigCredentialSource> out = new ArrayList<>(2);
        addIfNonNull(out, roleSource(properties));
        addIfNonNull(out, AwsConfigCredentialSource.SsoSession.fromProperties(properties));
        addIfNonNull(out, AwsConfigCredentialSource.LegacySso.fromProperties(properties));
        addIfNonNull(out, AwsConfigCredentialSource.LoginSession.fromProperties(properties));
        addIfNonNull(out, AwsConfigCredentialSource.CredentialProcess.fromProperties(properties));
        addIfNonNull(out, staticOrSessionKeys(properties));
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns WebIdentityToken if both role_arn and web_identity_token_file are set,
     * otherwise AssumeRole if role_arn is set, otherwise null.
     */
    private static AwsConfigCredentialSource roleSource(Map<String, String> p) {
        String roleArn = p.get("role_arn");
        if (roleArn == null || roleArn.isEmpty()) {
            return null;
        }
        String tokenFile = p.get("web_identity_token_file");
        if (tokenFile != null && !tokenFile.isEmpty()) {
            return new AwsConfigCredentialSource.WebIdentityToken(
                    roleArn,
                    tokenFile,
                    p.get("role_session_name"),
                    p.get("region"));
        }
        return new AwsConfigCredentialSource.AssumeRole(
                roleArn,
                p.get("source_profile"),
                p.get("credential_source"),
                p.get("external_id"),
                p.get("role_session_name"),
                p.get("mfa_serial"),
                parseIntOrNull(p.get("duration_seconds")),
                p.get("region"));
    }

    /**
     * Returns SessionKeys if session token is present, StaticKeys if only access/secret are present,
     * otherwise null.
     */
    private static AwsConfigCredentialSource staticOrSessionKeys(Map<String, String> p) {
        String ak = p.get("aws_access_key_id");
        String sk = p.get("aws_secret_access_key");
        if (ak == null || ak.isEmpty() || sk == null || sk.isEmpty()) {
            return null;
        }
        String token = p.get("aws_session_token");
        String accountId = p.get("aws_account_id");
        if (token != null && !token.isEmpty()) {
            return new AwsConfigCredentialSource.SessionKeys(ak, sk, token, accountId);
        }
        return new AwsConfigCredentialSource.StaticKeys(ak, sk, accountId);
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void addIfNonNull(List<AwsConfigCredentialSource> list, AwsConfigCredentialSource source) {
        if (source != null) {
            list.add(source);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AwsProfile that)) {
            return false;
        }
        return name.equals(that.name) && properties.equals(that.properties) && subProperties.equals(that.subProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, properties, subProperties);
    }

    @Override
    public String toString() {
        return "AwsProfile[name=" + name + ", properties=" + properties + ", subProperties=" + subProperties + ']';
    }
}
