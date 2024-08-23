/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.aws.http.auth.scheme.sigv4;


import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.aws.http.auth.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

public class Sigv4SignerTest {
    @Test
    void manualTempTest() {
        var request = SmithyHttpRequest.builder()
            .method("POST")
            .httpVersion(SmithyHttpVersion.HTTP_1_1)
            .uri(URI.create("http://example.amazonaws.com/"))
            .build();
        request = request.withAddedHeaders("Host", "example.amazonaws.com");
        var identity = AwsCredentialsIdentity.create(
            "AKIDEXAMPLE",
            "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY"
        );
        var timestamp = Instant.parse("2015-08-30T12:36:00Z");
        var authProperties = AuthProperties.builder()
            .put(SigningProperties.REGION, "us-east-1")
            .put(SigningProperties.SERVICE, "service")
            .put(SigningProperties.TIMESTAMP, timestamp)
            .build();
        var signedRequest = SigV4Signer.INSTANCE.sign(request, identity, authProperties);

        System.out.println(signedRequest);
    }
}
