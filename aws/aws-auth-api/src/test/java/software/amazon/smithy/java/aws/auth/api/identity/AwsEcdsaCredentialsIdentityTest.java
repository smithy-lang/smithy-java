/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.auth.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AwsEcdsaCredentialsIdentityTest {

    private static final ECPrivateKey P256_KEY = generate("secp256r1");

    @Test
    void exposesTheSuppliedValues() {
        var expiration = Instant.parse("2026-06-01T00:00:00Z");
        var identity = AwsEcdsaCredentialsIdentity.create("AKID", P256_KEY, "token", expiration, "123456789012");

        assertThat(identity.accessKeyId()).isEqualTo("AKID");
        assertThat(identity.ecdsaPrivateKey()).isSameAs(P256_KEY);
        assertThat(identity.sessionToken()).isEqualTo("token");
        assertThat(identity.expirationTime()).isEqualTo(expiration);
        assertThat(identity.accountId()).isEqualTo("123456789012");
    }

    @Test
    void isAnAwsCredentialsIdentity() {
        AwsCredentialsIdentity identity = AwsEcdsaCredentialsIdentity.create("AKID", P256_KEY, null);

        assertThat(identity).isInstanceOf(AwsEcdsaCredentialsIdentity.class);
        assertThat(identity.sessionToken()).isNull();
    }

    @Test
    void hasNoSecretAccessKey() {
        var identity = AwsEcdsaCredentialsIdentity.create("AKID", P256_KEY, null);

        assertThatThrownBy(identity::secretAccessKey)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("SigV4a");
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> AwsEcdsaCredentialsIdentity.create(null, P256_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accessKeyId");
        assertThatThrownBy(() -> AwsEcdsaCredentialsIdentity.create("AKID", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ecdsaPrivateKey");
    }

    @Test
    void rejectsKeysOnOtherCurves() {
        var p384 = generate("secp384r1");

        assertThatThrownBy(() -> AwsEcdsaCredentialsIdentity.create("AKID", p384, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("P-256");
    }

    @Test
    void toStringRedactsTheKeyAndSessionToken() {
        var identity = AwsEcdsaCredentialsIdentity.create("AKID", P256_KEY, "the-session-token");

        assertThat(identity.toString())
                .contains("AKID")
                .doesNotContain("the-session-token")
                .doesNotContain(P256_KEY.getS().toString(16))
                .doesNotContain(P256_KEY.toString());
    }

    private static ECPrivateKey generate(String curve) {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(curve));
            return (ECPrivateKey) generator.generateKeyPair().getPrivate();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
