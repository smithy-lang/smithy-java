/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
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
}
