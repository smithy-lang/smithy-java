/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.rulesengine.aws.language.functions.IsVirtualHostableS3Bucket;

/**
 * Pins the local {@code aws.isVirtualHostableS3Bucket} scan to the library implementation it replaces.
 *
 * <p>Being faster is the only reason the local version exists, so every case is compared against
 * {@code IsVirtualHostableS3Bucket.isVirtualHostableBucket} rather than against hand-written
 * expectations.
 *
 * <p>The coverage is shaped by a bug in the host-label equivalent of this scan: the word-at-a-time loop
 * only engages at nine characters, and a test that only used short strings passed while the scan was
 * returning the wrong answer for a 20-character input. So the lengths either side of every word
 * boundary are swept with the awkward characters at every offset, and a seeded random pass follows.
 */
class BucketNameEquivalenceTest {

    private static final char[] ALPHABET =
            {'a', 'm', 'z', '0', '5', '9', '-', '.', 'A', 'Q', 'Z', '_', '/', ' ', (char) 200};

    @Test
    void agreesOnEveryShortString() {
        for (int a = 0; a < 128; a++) {
            assertAgrees(String.valueOf((char) a));
            for (int b = 0; b < 128; b++) {
                assertAgrees(new String(new char[] {(char) a, (char) b}));
            }
        }
    }

    @Test
    void agreesOnEveryThreeCharacterStringOverTheInterestingAlphabet() {
        for (char a : ALPHABET) {
            for (char b : ALPHABET) {
                for (char c : ALPHABET) {
                    assertAgrees(new String(new char[] {a, b, c}));
                }
            }
        }
    }

    @Test
    void agreesAcrossTheWordScanBoundaries() {
        for (int len = 1; len <= 26; len++) {
            for (char fill : ALPHABET) {
                for (char odd : ALPHABET) {
                    for (int at = 0; at < len; at++) {
                        var chars = new char[len];
                        Arrays.fill(chars, fill);
                        chars[at] = odd;
                        assertAgrees(new String(chars));
                    }
                }
            }
        }
    }

    @Test
    void agreesOnRandomizedNames() {
        var random = new Random(20261012L);
        for (int i = 0; i < 200_000; i++) {
            int len = 1 + random.nextInt(70);
            var chars = new char[len];
            for (int j = 0; j < len; j++) {
                chars[j] = ALPHABET[random.nextInt(ALPHABET.length)];
            }
            assertAgrees(new String(chars));
        }
    }

    @Test
    void agreesOnRealisticAndAdversarialNames() {
        var cases = new ArrayList<>(List.of(
                "abc",
                "ab",
                "my-bucket-name",
                "bucket-name",
                "mybucket--abcd-ab1--x-s3",
                "-leading-hyphen",
                "trailing-hyphen-",
                "has.dot",
                "has..dots",
                "has.-adjacent",
                "has-.adjacent",
                "UPPERCASE",
                "MixedCase",
                "under_score",
                "has space",
                "192.168.1.1",
                "10.0.0.1",
                "not.an.ip.addr",
                "héllo-bucket"));
        cases.add("a".repeat(63));
        cases.add("a".repeat(64));
        cases.add("a" + "-".repeat(61) + "a");
        for (String value : cases) {
            assertAgrees(value);
        }
    }

    private static void assertAgrees(String value) {
        for (boolean allowSubdomains : new boolean[] {false, true}) {
            assertEquals(IsVirtualHostableS3Bucket.isVirtualHostableBucket(value, allowSubdomains),
                    AwsRulesFunction.AWS_IS_VIRTUAL_HOSTED_BUCKET.apply2(value, allowSubdomains),
                    () -> "disagreed on \"" + value + "\" allowSubdomains=" + allowSubdomains);
        }
    }
}
