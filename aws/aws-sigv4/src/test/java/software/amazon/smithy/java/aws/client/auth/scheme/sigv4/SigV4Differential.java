/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.http.api.HttpRequest;

final class SigV4Differential {

    record Outcome(String failure, String signature, Map<String, List<String>> headers) {
        static Outcome of(Callable<SignResult<HttpRequest>> signing) {
            try {
                var result = signing.call();
                return new Outcome(null, result.signature(), new TreeMap<>(result.signedRequest().headers().map()));
            } catch (Exception e) {
                return new Outcome(e.getClass().getName(), null, null);
            }
        }
    }

    private SigV4Differential() {}

    static void check(SigV4CaseGenerator.SignCase c, SigV4Signer signer) {
        Outcome first = Outcome.of(() -> signer.sign(c.request(), c.identity(), c.properties()));
        Outcome second = Outcome.of(() -> signer.sign(c.request(), c.identity(), c.properties()));
        assertThat(second).as("re-signing with the same signer: %s", c).isEqualTo(first);

        Outcome reference = Outcome.of(() -> StringSigV4Reference.sign(c.request(), c.identity(), c.properties()));
        assertThat(first).as("string reference: %s", c).isEqualTo(reference);
    }
}
