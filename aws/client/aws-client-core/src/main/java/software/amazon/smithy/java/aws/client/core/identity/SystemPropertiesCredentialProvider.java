/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.identity;

import java.util.Set;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.credentials.chain.ChainIdentityProvider;
import software.amazon.smithy.java.aws.credentials.chain.ChainSetup;
import software.amazon.smithy.java.aws.credentials.chain.CredentialFeatureId;
import software.amazon.smithy.java.aws.credentials.chain.OrderingConstraint;
import software.amazon.smithy.java.aws.credentials.chain.StandardProvider;

public final class SystemPropertiesCredentialProvider implements ChainIdentityProvider {

    private static final Set<CredentialFeatureId> FEATURE_IDS = Set.of(new CredentialFeatureId("f"));

    @Override
    public String name() {
        return "JavaSystemProperties";
    }

    @Override
    public OrderingConstraint ordering() {
        return new OrderingConstraint.Standard(StandardProvider.JAVA_SYSTEM_PROPERTIES);
    }

    @Override
    public Set<CredentialFeatureId> featureIds() {
        return FEATURE_IDS;
    }

    @Override
    public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
        if (identityType != AwsCredentialsIdentity.class) {
            return;
        }

        // Snapshot the system properties once at assembly. Registering a terminal resolver commits the chain to
        // this slot, so the decision and the credential value must come from the same read: otherwise a property
        // cleared between assembly and first resolve would strand the (terminal) chain on a NOT_FOUND result.
        String accessKey = System.getProperty(SystemPropertiesIdentityResolver.ACCESS_KEY_PROPERTY);
        String secretKey = System.getProperty(SystemPropertiesIdentityResolver.SECRET_KEY_PROPERTY);
        if (accessKey == null || secretKey == null) {
            return;
        }

        String sessionToken = System.getProperty(SystemPropertiesIdentityResolver.SESSION_TOKEN_PROPERTY);
        String accountId = System.getProperty(SystemPropertiesIdentityResolver.ACCOUNT_ID_PROPERTY);
        var identity = AwsCredentialsIdentity.create(accessKey, secretKey, sessionToken, null, accountId);
        setup.addTerminalResolver(IdentityResolver.of(identity));
    }
}
