/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.rulesengine.language.syntax.expressions.functions.IsValidHostLabel;

/**
 * Pins {@link EndpointUtils#isValidHostLabel} to the library implementation it replaces.
 *
 * <p>The local version exists only to be faster, so the only thing that matters is that it never
 * disagrees. Rather than assert hand-picked expectations, every case is compared against
 * {@code IsValidHostLabel.isValidHostLabel}, which is the definition of correct here.
 */
class HostLabelEquivalenceTest {

    @Test
    void agreesOnEveryShortAsciiString() {
        // Exhaustive over all one- and two-character strings drawn from the full ASCII range, which
        // covers every boundary of the digit, letter and hyphen classes plus the dot handling.
        int cases = 0;
        for (int a = 0; a < 128; a++) {
            assertAgrees(String.valueOf((char) a));
            for (int b = 0; b < 128; b++) {
                assertAgrees(new String(new char[] {(char) a, (char) b}));
                cases++;
            }
        }
        assertEquals(128 * 128, cases);
    }

    @Test
    void agreesOnEveryThreeCharacterStringOverTheInterestingAlphabet() {
        // Three characters is where "first position differs from the rest" and dot-splitting interact.
        // Restricted to the characters that sit on a class boundary, so the product stays small.
        char[] alphabet = {'a', 'z', 'A', 'Z', '0', '9', '-', '.', '_', '/', ' ', '\0', 'é', (char) 127};
        for (char a : alphabet) {
            for (char b : alphabet) {
                for (char c : alphabet) {
                    assertAgrees(new String(new char[] {a, b, c}));
                }
            }
        }
    }

    @Test
    void agreesOnRealisticAndAdversarialLabels() {
        List<String> cases = new ArrayList<>(List.of(
                "",
                "a",
                "my-bucket-name",
                "us-west-2",
                "amazonaws.com",
                "s3.us-west-2.amazonaws.com",
                "-leading-hyphen",
                "trailing-hyphen-",
                "1starts-with-digit",
                ".leading-dot",
                "trailing-dot.",
                "double..dot",
                "..",
                ".",
                "UPPER",
                "MiXeD-case",
                "under_score",
                "has space",
                "has/slash",
                "has:colon",
                "pun!ct",
                "héllo",
                "xn--punycode",
                "192.168.1.1"));
        // Exactly 63 characters is valid, 64 is not; also check those boundaries inside a dotted name.
        cases.add("a".repeat(63));
        cases.add("a".repeat(64));
        cases.add("a".repeat(63) + "." + "b".repeat(63));
        cases.add("a".repeat(64) + ".b");
        cases.add("b." + "a".repeat(64));
        for (String value : cases) {
            assertAgrees(value);
        }
    }

    @Test
    void agreesAcrossTheWordScanBoundaries() {
        // The byte-at-a-time scan only hands off to the eight-at-a-time loop at nine characters, so the
        // cases above never reached it. A borrow escaping one lane and corrupting the next produced a
        // wrong answer for "op-01234567890123456" that every case above missed, which is what this
        // covers: every length either side of a word boundary, with the awkward characters placed at
        // every offset.
        char[] alphabet = {'a', 'z', '0', '9', '-', '.', 'A', 'Z', '_'};
        for (int len = 1; len <= 26; len++) {
            for (char fill : alphabet) {
                for (char odd : alphabet) {
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
    void agreesOnRandomizedLabels() {
        // Fixed seed, so a failure is reproducible. Two awkward characters per string rather than one,
        // to catch interactions a single-character probe cannot.
        char[] alphabet = {'a', 'm', 'z', '0', '5', '9', '-', '.', 'A', 'Q', 'Z', '_', '/', ' ', (char) 200};
        var random = new Random(20261012L);
        for (int i = 0; i < 200_000; i++) {
            int len = 1 + random.nextInt(40);
            var chars = new char[len];
            for (int j = 0; j < len; j++) {
                chars[j] = alphabet[random.nextInt(alphabet.length)];
            }
            assertAgrees(new String(chars));
        }
    }

    @Test
    void rejectsNull() {
        // The library throws on null, so only the local version's contract is asserted here: the
        // compiled nodes rely on null reading as invalid rather than failing the resolution.
        assertFalse(EndpointUtils.isValidHostLabel(null, false));
        assertFalse(EndpointUtils.isValidHostLabel(null, true));
    }

    private static void assertAgrees(String value) {
        for (boolean allowDots : new boolean[] {false, true}) {
            assertEquals(IsValidHostLabel.isValidHostLabel(value, allowDots),
                    EndpointUtils.isValidHostLabel(value, allowDots),
                    () -> "disagreed on " + quote(value) + " allowDots=" + allowDots);
        }
    }

    private static String quote(String value) {
        var sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c < 0x7F) {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04x", (int) c));
            }
        }
        return sb.append('"').toString();
    }
}
