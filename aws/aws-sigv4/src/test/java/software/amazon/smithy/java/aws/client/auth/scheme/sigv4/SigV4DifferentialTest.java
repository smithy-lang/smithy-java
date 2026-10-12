/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class SigV4DifferentialTest {

    private static final int CASES = 6_000;

    @Test
    void sharedSignerAcrossRandomRequests() {
        var source = SigV4CaseGenerator.of(new Random(0x5169_4D4L));
        try (var signer = SigV4Signer.create()) {
            for (int i = 0; i < CASES; i++) {
                SigV4Differential.check(SigV4CaseGenerator.next(source), signer);
            }
        }
    }

    @Test
    void freshSignerPerRequest() {
        var source = SigV4CaseGenerator.of(new Random(0xC0FFEEL));
        for (int i = 0; i < CASES / 3; i++) {
            try (var signer = SigV4Signer.create()) {
                SigV4Differential.check(SigV4CaseGenerator.next(source), signer);
            }
        }
    }

    @Test
    void concurrentSignersOnPlatformAndVirtualThreads() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService platform = Executors.newFixedThreadPool(8);
                ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 32; t++) {
                long seed = 1_000L + t;
                var pool = t % 2 == 0 ? platform : virtual;
                futures.add(pool.submit(() -> {
                    var source = SigV4CaseGenerator.of(new Random(seed));
                    for (int i = 0; i < 300; i++) {
                        if (i % 2 == 0) {
                            try (var signer = SigV4Signer.create()) {
                                SigV4Differential.check(SigV4CaseGenerator.next(source), signer);
                            }
                        } else {
                            try (var signer = SigV4Signer.create()) {
                                for (int n = 0; n < 5; n++) {
                                    SigV4Differential.check(SigV4CaseGenerator.next(source), signer);
                                }
                            }
                        }
                    }
                }));
            }
            for (var f : futures) {
                f.get();
            }
        }
    }
}
