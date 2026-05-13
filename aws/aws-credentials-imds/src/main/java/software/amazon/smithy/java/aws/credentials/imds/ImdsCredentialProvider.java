/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.imds;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.CachingIdentityResolver;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsResolver;
import software.amazon.smithy.java.aws.config.AwsProfile;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.CreateResult;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.ProviderContext;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Credential provider that fetches credentials from the EC2 Instance Metadata Service (IMDS).
 *
 * <p>Registers in the {@link StandardProvider#EC2_INSTANCE_METADATA} chain slot. Uses IMDSv2 exclusively
 * (no v1 fallback). Credentials are cached with static stability enabled per the AWS Static Stability SEP.
 */
public final class ImdsCredentialProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("0"));

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ImdsCredentialProvider.class);
    private static final URI DEFAULT_ENDPOINT = URI.create("http://169.254.169.254");
    private static final JsonCodec CODEC = JsonCodec.builder().build();

    @Override
    public String name() {
        return "Ec2InstanceMetadata";
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.EC2_INSTANCE_METADATA);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends Identity> CreateResult<I> create(Class<I> identityType, ProviderContext context) {
        if (identityType != AwsCredentialsIdentity.class) {
            return CreateResult.pass();
        }

        AwsProfileFile profileFile = context.profileFile();
        if (isDisabled(profileFile)) {
            return CreateResult.pass();
        }

        URI endpoint = resolveEndpoint();
        String profileName = resolveProfileName(profileFile);
        ImdsClient client = new ImdsClient(endpoint);
        AwsCredentialsResolver delegate = ctx -> fetchAndParse(client, profileName);

        return (CreateResult<I>) new CreateResult.PossibleMatch<>(CachingIdentityResolver.builder(delegate)
                .executor(context.executor())
                .allowExpiredCredentials(true)
                .build());
    }

    private static IdentityResult<AwsCredentialsIdentity> fetchAndParse(ImdsClient client, String profileName) {
        String json;
        try {
            json = client.fetchCredentials(profileName);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return IdentityResult.ofError(ImdsCredentialProvider.class,
                    "Failed to fetch credentials from IMDS: " + e.getMessage());
        }

        Document doc = CODEC.createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
        String code = stringMember(doc, "Code");
        if (!"Success".equals(code)) {
            return IdentityResult.ofError(ImdsCredentialProvider.class, "IMDS returned non-success code: " + code);
        }

        String accessKeyId = stringMember(doc, "AccessKeyId");
        String secretAccessKey = stringMember(doc, "SecretAccessKey");
        if (accessKeyId == null || secretAccessKey == null) {
            return IdentityResult.ofError(ImdsCredentialProvider.class,
                    "IMDS response missing AccessKeyId or SecretAccessKey");
        }

        String sessionToken = stringMember(doc, "Token");
        String accountId = stringMember(doc, "AccountId");
        String expirationStr = stringMember(doc, "Expiration");
        Instant expiration = null;
        if (expirationStr != null) {
            try {
                expiration = Instant.parse(expirationStr);
            } catch (DateTimeParseException e) {
                LOGGER.warn("IMDS returned unparseable Expiration: {}", expirationStr);
            }
        }

        return IdentityResult.of(AwsCredentialsIdentity.create(
                accessKeyId,
                secretAccessKey,
                sessionToken,
                expiration,
                accountId));
    }

    private static String stringMember(Document doc, String name) {
        Document member = doc.getMember(name);
        return member == null ? null : member.asString();
    }

    private static boolean isDisabled(AwsProfileFile profileFile) {
        // Priority: system property > env var > config file. First non-null value wins.
        String value = System.getProperty("aws.disableEc2Metadata");
        if (value == null) {
            value = System.getenv("AWS_EC2_METADATA_DISABLED");
        }
        if (value == null) {
            value = getProfileProperty(profileFile, "disable_ec2_metadata");
        }
        return "true".equalsIgnoreCase(value);
    }

    private static URI resolveEndpoint() {
        String override = System.getenv("AWS_EC2_METADATA_SERVICE_ENDPOINT");
        return override != null && !override.isEmpty() ? URI.create(override) : DEFAULT_ENDPOINT;
    }

    private static String resolveProfileName(AwsProfileFile profileFile) {
        // Priority: system property > env var > config file
        String prop = System.getProperty("aws.ec2InstanceProfileName");
        if (prop != null) {
            return ensureNotBlank("aws.ec2InstanceProfileName", prop);
        }

        String env = System.getenv("AWS_EC2_INSTANCE_PROFILE_NAME");
        if (env != null) {
            return ensureNotBlank("AWS_EC2_INSTANCE_PROFILE_NAME", env);
        }

        String config = getProfileProperty(profileFile, "ec2_instance_profile_name");
        if (config != null) {
            return ensureNotBlank("ec2_instance_profile_name", config);
        }

        // Will be discovered from IMDS.
        return null;
    }

    private static String ensureNotBlank(String name, String value) {
        if (value.isBlank()) {
            throw new IllegalStateException(name + " is set but blank");
        }
        return value;
    }

    private static String getProfileProperty(AwsProfileFile profileFile, String key) {
        if (profileFile == null) {
            return null;
        }

        // Resolve profile name
        String profileName = System.getenv("AWS_PROFILE");
        if (profileName == null || profileName.isEmpty()) {
            profileName = "default";
        }

        AwsProfile profile = profileFile.profile(profileName);
        return profile != null ? profile.property(key) : null;
    }

    /** Resolver returned when IMDS is disabled via configuration. */
    private static final class DisabledResolver implements AwsCredentialsResolver {
        private static final IdentityResult<AwsCredentialsIdentity> DISABLED = IdentityResult.ofError(
                ImdsCredentialProvider.class,
                "IMDS credential fetching is disabled");

        @Override
        public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context requestProperties) {
            return DISABLED;
        }
    }
}
