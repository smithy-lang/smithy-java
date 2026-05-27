/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;

class S3ExpressIdentityKeyTest {

    @Test
    void differentBucketsAreNotEqual() {
        var creds = AwsCredentialsIdentity.create("AKID", "secret");
        var a = new S3ExpressIdentityKey("bucket-a", creds);
        var b = new S3ExpressIdentityKey("bucket-b", creds);
        assertThat(a, not(equalTo(b)));
    }

    @Test
    void differentBaseAccessKeysAreNotEqual() {
        var c1 = AwsCredentialsIdentity.create("AKID-1", "secret");
        var c2 = AwsCredentialsIdentity.create("AKID-2", "secret");
        var a = new S3ExpressIdentityKey("bucket", c1);
        var b = new S3ExpressIdentityKey("bucket", c2);
        assertThat(a, not(equalTo(b)));
    }

    @Test
    void differentExpirationButSameKeysIsEqual() {
        // Token rotation that produces the same access/secret/session is reusable from a
        // confused-deputy standpoint, so we let it cache-hit.
        var c1 = AwsCredentialsIdentity.create("AKID", "secret", "session", Instant.parse("2026-01-01T00:00:00Z"));
        var c2 = AwsCredentialsIdentity.create("AKID", "secret", "session", Instant.parse("2026-12-01T00:00:00Z"));
        var a = new S3ExpressIdentityKey("bucket", c1);
        var b = new S3ExpressIdentityKey("bucket", c2);
        assertThat(a, equalTo(b));
        assertThat(a.hashCode(), is(b.hashCode()));
    }

    @Test
    void differentSessionTokensAreNotEqual() {
        var c1 = AwsCredentialsIdentity.create("AKID", "secret", "session-1");
        var c2 = AwsCredentialsIdentity.create("AKID", "secret", "session-2");
        var a = new S3ExpressIdentityKey("bucket", c1);
        var b = new S3ExpressIdentityKey("bucket", c2);
        assertThat(a, not(equalTo(b)));
    }

    @Test
    void hashIsStableAcrossEqualKeys() {
        var c = AwsCredentialsIdentity.create("AKID", "secret");
        assertThat(new S3ExpressIdentityKey("bucket", c).hashCode(),
                is(new S3ExpressIdentityKey("bucket", c).hashCode()));
    }
}
