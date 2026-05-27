/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4Settings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;

class S3ExpressSignerTest {

    private static final S3ExpressIdentity IDENTITY = S3ExpressIdentity.create(
            "AKIDEXAMPLE",
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            "session-token-blob",
            null);

    @Test
    void addsS3SessionTokenHeaderAndOmitsSecurityToken() {
        var signer = S3ExpressSigner.create("s3express");
        try {
            var request = baseRequest("/key");
            var properties = signerContext();

            var signed = signer.sign(request, IDENTITY, properties).signedRequest();

            // s3session-token is set with the identity's session token.
            assertThat(signed.headers().firstValue("x-amz-s3session-token"), is(equalTo("session-token-blob")));

            // x-amz-security-token MUST NOT be set per the SEP, even though the identity has a
            // session token.
            assertThat(signed.headers().firstValue("x-amz-security-token"), is(nullValue()));

            // The Authorization header is present and references the s3session-token in
            // SignedHeaders, proving the inner SigV4 signer covered it in the canonical request.
            String auth = signed.headers().firstValue("authorization");
            assertThat(auth, containsString("AWS4-HMAC-SHA256"));
            assertThat(auth, containsString("x-amz-s3session-token"));
        } finally {
            signer.close();
        }
    }

    @Test
    void closeReleasesUnderlyingSigningResources() {
        // Two close()s on the same signer must not blow up. Mostly a smoke test for the
        // delegate close hookup.
        var signer = S3ExpressSigner.create("s3express");
        signer.close();
        signer.close();
    }

    private static HttpRequest baseRequest(String path) {
        return HttpRequest.create()
                .setMethod("GET")
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setUri(URI
                        .create("https://my-bucket--use1-az4--x-s3.s3express-use1-az4.us-east-1.amazonaws.com" + path))
                .setHeaders(HttpHeaders.of(Map.of("Host",
                        List.of("my-bucket--use1-az4--x-s3.s3express-use1-az4.us-east-1.amazonaws.com"))))
                .toUnmodifiable();
    }

    private static Context signerContext() {
        var ctx = Context.create();
        ctx.put(SigV4Settings.SIGNING_NAME, "s3express");
        ctx.put(RegionSetting.REGION, "us-east-1");
        return ctx;
    }
}
