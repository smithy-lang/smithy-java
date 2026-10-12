/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Run with {@code ./gradlew :aws:aws-sigv4:fuzz -Pfuzz.maxDuration=10m}.
 */
class SigV4DifferentialFuzzTest {

    @FuzzTest(maxDuration = "10m")
    void signsLikeTheReference(FuzzedDataProvider data) {
        var source = new FuzzedSource(data);
        int cases = 1 + data.consumeInt(0, 3);
        try (var signer = SigV4Signer.create()) {
            for (int i = 0; i < cases; i++) {
                SigV4Differential.check(SigV4CaseGenerator.next(source), signer);
            }
        }
    }
}
