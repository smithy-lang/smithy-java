/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import javax.crypto.Mac;
import org.junit.jupiter.api.Test;

class SigningResourcesTest {

    @Test
    void hexMatchesHexFormatForEveryLengthAndValue() {
        var random = new Random(7);
        for (int len = 0; len <= 40; len++) {
            for (int trial = 0; trial < 20; trial++) {
                byte[] in = new byte[len];
                random.nextBytes(in);
                byte[] out = new byte[len * 2 + 3];
                int end = SigningResources.writeHex(in, out, 1);
                assertThat(end).isEqualTo(1 + len * 2);
                assertThat(new String(out, 1, len * 2, StandardCharsets.US_ASCII))
                        .isEqualTo(HexFormat.of().formatHex(in));
            }
        }
        byte[] all = new byte[256];
        for (int i = 0; i < 256; i++) {
            all[i] = (byte) i;
        }
        byte[] out = new byte[512];
        SigningResources.writeHex(all, out, 0);
        assertThat(new String(out, StandardCharsets.US_ASCII)).isEqualTo(HexFormat.of().formatHex(all));
    }

    @Test
    void headerValuesAreTrimmedCollapsedAndUtf8Encoded() {
        var r = new SigningResources();
        for (String[] io : new String[][] {
                {"plain", "plain"},
                {"  lead", "lead"},
                {"trail \t ", "trail"},
                {"a   b\t\tc", "a b c"},
                {"\t\t", ""},
                {"café", "cafÃ©"}, // é (U+00E9) as UTF-8 0xC3 0xA9
                {" naïve  value ", "naÃ¯ve value"},
                {"exactly8", "exactly8"},
                {"sixteen chars!!!", "sixteen chars!!!"},
                {"seventeen chars!!", "seventeen chars!!"},
        }) {
            r.beginOutput();
            r.putHeaderValue(io[0]);
            String got = new String(Arrays.copyOf(r.out, r.outLength), StandardCharsets.ISO_8859_1);
            assertThat(got).as("value %s", io[0]).isEqualTo(io[1]);
        }
    }

    @Test
    void excludedHeadersNeverEnterTheSigningSet() {
        var r = new SigningResources();
        r.addHeaderForSigning("Authorization", "x");
        r.addHeaderForSigning("X-Amz-Date", "x");
        r.addHeaderForSigning("User-Agent", "x");
        r.addHeaderForSigning("Content-Length", "1");
        r.addHeaderForSigning("Content-Type", "text/plain");
        r.addHeaderForSigning("x-custom", "v");
        assertThat(r.headerCount).isEqualTo(2);
        assertThat(r.headers[0]).isEqualTo("content-type");
        assertThat(r.headers[2]).isEqualTo("x-custom");
    }

    @Test
    void uppercaseDetectionIsExactForEveryByteAndLane() {
        var random = new Random(11);
        byte[] word = new byte[8];
        for (int lane = 0; lane < 8; lane++) {
            for (int value = 0; value < 256; value++) {
                for (int i = 0; i < 8; i++) {
                    int filler = random.nextInt(256);
                    while (filler >= 'A' && filler <= 'Z') {
                        filler = random.nextInt(256);
                    }
                    word[i] = (byte) filler;
                }
                word[lane] = (byte) value;
                long x = ByteBuffer.wrap(word).order(ByteOrder.LITTLE_ENDIAN).getLong();
                boolean expected = value >= 'A' && value <= 'Z';
                assertThat(SigningResources.hasUppercaseAscii(x)).as("lane %d value %d", lane, value)
                        .isEqualTo(expected);
            }
        }
    }

    @Test
    void sortMemoReplaysTheSameOrderAndResortsNewSequences() {
        var r = new SigningResources();
        String host = "host";
        String ct = "content-type";
        String dup = "x-dup";
        r.addHeaderCanonical(dup, "second");
        r.addHeaderCanonical(host, "h");
        r.addHeaderCanonical(dup, "first");
        r.addHeaderCanonical(ct, "c");
        r.sortHeadersByName();
        assertThat(names(r)).containsExactly(ct, host, dup, dup);
        assertThat(values(r)).containsExactly("c", "h", "second", "first");

        r.reset();
        r.addHeaderCanonical(dup, "two");
        r.addHeaderCanonical(host, "hh");
        r.addHeaderCanonical(dup, "one");
        r.addHeaderCanonical(ct, "cc");
        r.sortHeadersByName();
        assertThat(names(r)).containsExactly(ct, host, dup, dup);
        assertThat(values(r)).containsExactly("cc", "hh", "two", "one");

        r.reset();
        r.addHeaderCanonical("z-last", "z");
        r.addHeaderCanonical(host, "h");
        r.addHeaderCanonical("a-first", "a");
        r.sortHeadersByName();
        assertThat(names(r)).containsExactly("a-first", host, "z-last");
        assertThat(values(r)).containsExactly("a", "h", "z");

        r.reset();
        r.addHeaderCanonical("z-last", "z");
        r.addHeaderCanonical(host, "h");
        r.addHeaderCanonical("b-second", "b");
        r.sortHeadersByName();
        assertThat(names(r)).containsExactly("b-second", host, "z-last");
    }

    @Test
    void headersSortByNameStablyForEverySizeAndOrder() {
        var random = new Random(17);
        var r = new SigningResources();
        for (int n : new int[] {0, 1, 32, 33, 1_000}) {
            for (int mode = 0; mode < 3; mode++) {
                List<String[]> headers = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    headers.add(new String[] {"h" + random.nextInt(Math.max(1, n / 4)), "v" + i});
                }
                if (mode == 0) {
                    headers.sort((a, b) -> b[0].compareTo(a[0]));
                } else if (mode == 1) {
                    headers.sort((a, b) -> a[0].compareTo(b[0]));
                }
                List<String[]> expected = new ArrayList<>(headers);
                expected.sort((a, b) -> a[0].compareTo(b[0]));
                r.reset();
                for (String[] h : headers) {
                    r.addHeaderCanonical(h[0], h[1]);
                }
                r.sortHeadersByName();
                List<String> got = new ArrayList<>();
                for (int i = 0; i < r.headerCount; i++) {
                    got.add(r.headers[i * 2] + ":" + r.headers[i * 2 + 1]);
                }
                List<String> want = new ArrayList<>();
                for (String[] h : expected) {
                    want.add(h[0] + ":" + h[1]);
                }
                assertThat(got).as("n %d mode %d", n, mode).isEqualTo(want);
            }
        }
    }

    @Test
    void queryPairsSortByKeyThenValueForEverySizeAndOrder() {
        var random = new Random(13);
        var r = new SigningResources();
        for (int n : new int[] {0, 1, 32, 33, 1_000}) {
            for (int mode = 0; mode < 3; mode++) {
                List<String[]> pairs = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    pairs.add(new String[] {"k" + random.nextInt(Math.max(1, n / 4)), "v" + random.nextInt(1000)});
                }
                List<String[]> expected = new ArrayList<>(pairs);
                expected.sort((a, b) -> {
                    int cmp = a[0].compareTo(b[0]);
                    return cmp != 0 ? cmp : a[1].compareTo(b[1]);
                });
                List<String[]> input = new ArrayList<>(expected);
                if (mode == 0) {
                    Collections.reverse(input);
                } else if (mode == 2) {
                    input = pairs;
                }
                r.reset();
                for (String[] p : input) {
                    r.addQueryPair(p[0], p[1]);
                }
                r.sortQueryPairs();
                List<String> got = new ArrayList<>();
                for (int i = 0; i < r.queryPairCount; i++) {
                    got.add(r.queryPairs[i * 2] + "=" + r.queryPairs[i * 2 + 1]);
                }
                List<String> want = new ArrayList<>();
                for (String[] p : expected) {
                    want.add(p[0] + "=" + p[1]);
                }
                assertThat(got).as("n %d mode %d", n, mode).isEqualTo(want);
            }
        }
    }

    private static List<String> names(SigningResources r) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < r.headerCount; i++) {
            out.add(r.headers[i * 2]);
        }
        return out;
    }

    private static List<String> values(SigningResources r) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < r.headerCount; i++) {
            out.add(r.headers[i * 2 + 1]);
        }
        return out;
    }

    @Test
    void hashingHonorsTheDefaultJcaLookupUnlessBuiltInProvidersArePreferred() throws Exception {
        assertThat(System.getProperty(SigningResources.PREFER_BUILTIN_HASH_PROVIDERS_PROPERTY)).isNull();
        var resources = new SigningResources();
        assertThat(resources.sha256Digest.getProvider().getName())
                .isEqualTo(MessageDigest.getInstance("SHA-256").getProvider().getName());
        assertThat(resources.sha256Mac.getProvider().getName())
                .isEqualTo(Mac.getInstance("HmacSHA256").getProvider().getName());

        assertThat(SigningResources.newSha256(true).getProvider().getName()).isEqualTo("SUN");
        assertThat(SigningResources.newHmacSha256(true).getProvider().getName()).isEqualTo("SunJCE");
    }

    @Test
    void regionSetHeaderValueTracksMutationsOfTheSameList() {
        var resources = new SigningResources();
        var regions = new ArrayList<>(List.of("us-east-1"));
        assertThat(resources.regionSetHeaderValue(regions)).isEqualTo("us-east-1");

        regions.set(0, "us-west-2");
        assertThat(resources.regionSetHeaderValue(regions)).isEqualTo("us-west-2");

        regions.add(" us-east-1 ");
        assertThat(resources.regionSetHeaderValue(regions)).isEqualTo("us-west-2,us-east-1");
        assertThat(resources.regionSetHeaderValue(List.of("us-west-2", "us-east-1"))).isEqualTo("us-west-2,us-east-1");
    }
}
