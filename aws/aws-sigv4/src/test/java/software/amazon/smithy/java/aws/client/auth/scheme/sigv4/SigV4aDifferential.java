/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import software.amazon.smithy.java.io.datastream.PooledDataStream;

final class SigV4aDifferential {

    record Outcome(
            String failure,
            String canonicalRequest,
            String stringToSign,
            Map<String, List<String>> headersWithoutSignature) {}

    private SigV4aDifferential() {}

    static void check(SigV4CaseGenerator.SignCase c, SigV4aSigner signer) {
        String firstSignature = null;
        Outcome first;
        try {
            var result = signer.sign(c.request(), c.identity(), c.properties());
            firstSignature = result.signature();
            first = new Outcome(null,
                    signer.lastCanonicalRequest(),
                    signer.lastStringToSign(),
                    withoutSignature(result.signedRequest().headers().map()));
            assertThat(SigV4aTestSupport.signatureOf(result.signedRequest().headers().firstValue("authorization")))
                    .isEqualTo(firstSignature);
        } catch (Exception e) {
            first = new Outcome(e.getClass().getName(), null, null, null);
        }

        Outcome second;
        try {
            var result = signer.sign(c.request(), c.identity(), c.properties());
            second = new Outcome(null,
                    signer.lastCanonicalRequest(),
                    signer.lastStringToSign(),
                    withoutSignature(result.signedRequest().headers().map()));
        } catch (Exception e) {
            second = new Outcome(e.getClass().getName(), null, null, null);
        }
        assertThat(second).as("re-signing with the same signer: %s", c).isEqualTo(first);
        if (c.request().body() instanceof PooledDataStream pooled) {
            assertThat(pooled.escaped()).as("signing must not alias a pooled body: %s", c).isFalse();
        }

        Outcome reference;
        try {
            var expected = StringSigV4Reference.sigV4a(c.request(), c.identity(), c.properties());
            reference = new Outcome(null, expected.canonicalRequest(), expected.stringToSign(), expected.headers());
        } catch (Exception e) {
            reference = new Outcome(e.getClass().getName(), null, null, null);
        }
        assertThat(first).as("string reference: %s", c).isEqualTo(reference);

        if (first.failure == null) {
            var publicKey = SigV4aTestSupport.publicKeyFor(c.identity());
            assertThat(SigV4aTestSupport.verifies(publicKey, first.stringToSign, firstSignature))
                    .as("signature verifies: %s", c)
                    .isTrue();
        }
    }

    private static Map<String, List<String>> withoutSignature(Map<String, List<String>> headers) {
        var result = new TreeMap<String, List<String>>();
        headers.forEach((name, values) -> {
            if (name.equals("authorization")) {
                result.put(name, values.stream().map(SigV4aTestSupport::withoutSignature).toList());
            } else {
                result.put(name, values);
            }
        });
        return result;
    }
}
